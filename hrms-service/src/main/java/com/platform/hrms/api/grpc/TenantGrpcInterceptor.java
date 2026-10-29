package com.platform.hrms.api.grpc;

import com.platform.common.constants.PlatformHeaders;
import com.platform.common.tenant.TenantContext;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;

/**
 * gRPC equivalent of TenantFilter: bind x-tenant-id metadata → TenantContext.
 * Always clears in finally to avoid ThreadLocal leaks on gRPC worker threads.
 */
@GrpcGlobalServerInterceptor
public class TenantGrpcInterceptor implements ServerInterceptor {

    private static final Metadata.Key<String> TENANT_KEY =
            Metadata.Key.of(PlatformHeaders.GRPC_TENANT_ID, Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

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
