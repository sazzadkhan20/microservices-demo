package com.platform.people.client;

import com.platform.common.constants.PlatformHeaders;
import com.platform.common.security.InternalCalls;
import com.platform.common.tenant.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

/**
 * People → File-service S2S checks before attaching document links (guide §8).
 */
@Component
public class FilesGatewayClient {

    private final RestClient client;
    private final String serviceKey;
    private final String internalKey;

    public FilesGatewayClient(
            RestClient.Builder builder,
            @Value("${platform.files-base-url:http://127.0.0.1:8084}") String baseUrl,
            @Value("${platform.security.service-key}") String serviceKey,
            @Value("${platform.security.internal-key}") String internalKey) {
        this.client = builder.baseUrl(baseUrl).build();
        this.serviceKey = serviceKey;
        this.internalKey = internalKey;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> describe(Long fileId) {
        String tenantId = TenantContext.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalStateException("TenantContext required for file describe");
        }
        try {
            Map<String, Object> body = client.get()
                    .uri("/api/internal/files/{id}", fileId)
                    .header(PlatformHeaders.SERVICE_KEY, serviceKey)
                    .header(InternalCalls.HEADER, internalKey)
                    .header(PlatformHeaders.TENANT_ID, tenantId)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(Map.class);
            if (body == null) {
                throw new IllegalArgumentException("File not found: " + fileId);
            }
            return body;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                throw new IllegalArgumentException("File not found: " + fileId);
            }
            throw new IllegalStateException("File-service describe failed: " + e.getStatusCode().value(), e);
        }
    }

    /** Guide §8 — validate file exists (and object present) before creating the employee link. */
    public void assertAttachable(Long fileId) {
        Map<String, Object> desc = describe(fileId);
        Object present = desc.get("objectPresent");
        if (present instanceof Boolean b && !b) {
            throw new IllegalStateException("File object missing in storage for fileId=" + fileId);
        }
        String status = desc.get("scanStatus") == null ? "" : String.valueOf(desc.get("scanStatus"));
        if ("REJECTED".equalsIgnoreCase(status) || "INFECTED".equalsIgnoreCase(status)
                || "FAILED".equalsIgnoreCase(status)) {
            throw new IllegalStateException("Cannot attach rejected/infected file fileId=" + fileId);
        }
    }
}
