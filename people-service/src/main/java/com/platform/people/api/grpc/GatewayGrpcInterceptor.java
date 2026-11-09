package com.platform.people.api.grpc;

import com.platform.common.constants.PlatformHeaders;
import com.platform.common.security.SecretMatcher;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.beans.factory.annotation.Value;

/**
 * gRPC equivalent of {@code GatewayAuthFilter}: authenticates internal callers (Payroll) with the
 * service key ({@code x-service-key}). People has one fixed database, so no per-call routing context.
 */
@GrpcGlobalServerInterceptor
public class GatewayGrpcInterceptor implements ServerInterceptor {

    private static final Metadata.Key<String> SERVICE_KEY =
            Metadata.Key.of(PlatformHeaders.GRPC_SERVICE_KEY, Metadata.ASCII_STRING_MARSHALLER);

    private final boolean requireGateway;
    private final String serviceKey;

    public GatewayGrpcInterceptor(
            @Value("${platform.security.require-gateway:true}") boolean requireGateway,
            @Value("${platform.security.service-key}") String serviceKey) {
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
        return next.startCall(call, headers);
    }
}
