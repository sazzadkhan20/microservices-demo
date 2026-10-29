package com.platform.hrms.api.grpc;

import com.platform.common.constants.PlatformHeaders;
import com.platform.common.security.SecretMatcher;
import com.platform.common.tenant.TenantContext;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.beans.factory.annotation.Value;

/**
 * gRPC equivalent of TenantFilter: authenticates the caller with the shared service key
 * ({@code x-service-key}) and binds {@code x-tenant-id} metadata → TenantContext.
 * Always clears in finally to avoid ThreadLocal leaks on gRPC worker threads.
 */
@GrpcGlobalServerInterceptor
public class TenantGrpcInterceptor implements ServerInterceptor {

    private static final Metadata.Key<String> TENANT_KEY =
            Metadata.Key.of(PlatformHeaders.GRPC_TENANT_ID, Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> SERVICE_KEY =
            Metadata.Key.of(PlatformHeaders.GRPC_SERVICE_KEY, Metadata.ASCII_STRING_MARSHALLER);

    private final boolean requireGateway;
    private final String serviceKey;

    public TenantGrpcInterceptor(
            @Value("${platform.security.require-gateway:true}") boolean requireGateway,
            @Value("${platform.security.service-key:local-service-key-change-me}") String serviceKey) {
        this.requireGateway = requireGateway;
        this.serviceKey = serviceKey;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        if (requireGateway && !SecretMatcher.matches(headers.get(SERVICE_KEY), serviceKey)) {
            call.close(Status.UNAUTHENTICATED.withDescription("Missing or invalid x-service-key"), new Metadata());
            return new ServerCall.Listener<>() {
            };
        }

        String tenantId = headers.get(TENANT_KEY);
        if (tenantId == null || tenantId.isBlank()) {
            call.close(Status.INVALID_ARGUMENT.withDescription("Missing x-tenant-id metadata"), new Metadata());
            return new ServerCall.Listener<>() {
            };
        }

        TenantContext.setTenantId(tenantId.trim());
        ServerCall.Listener<ReqT> delegate = next.startCall(call, headers);
        return new ServerCall.Listener<>() {
            @Override
            public void onMessage(ReqT message) {
                delegate.onMessage(message);
            }

            @Override
            public void onHalfClose() {
                try {
                    delegate.onHalfClose();
                } finally {
                    TenantContext.clear();
                }
            }

            @Override
            public void onCancel() {
                try {
                    delegate.onCancel();
                } finally {
                    TenantContext.clear();
                }
            }

            @Override
            public void onComplete() {
                try {
                    delegate.onComplete();
                } finally {
                    TenantContext.clear();
                }
            }

            @Override
            public void onReady() {
                delegate.onReady();
            }
        };
    }
}
