package com.platform.report.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.platform.report.infrastructure.HrmsGatewayClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Resolves which employees a reporter may include, then applies org filters.
 * Uses reports:view scope (OWN/TEAM/ALL) + people employee list (already employees:view scoped).
 */
@Component
public class ReportScope {

    private final HrmsGatewayClient hrms;

    public ReportScope(HrmsGatewayClient hrms) {
        this.hrms = hrms;
    }

    public List<JsonNode> employees(
            Long actorId,
            Long employeeId,
            Long locationId,
            Long departmentId,
            Long teamId) {
        if (actorId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing employee identity");
        }
        String scope = hrms.reportsViewScope(actorId);
        List<JsonNode> all = hrms.listEmployees(actorId);
        List<JsonNode> out = new ArrayList<>();
        for (JsonNode e : all) {
            Long id = asLong(e.get("id"));
            if (id == null) {
                continue;
            }
            if ("OWN".equals(scope) && !id.equals(actorId)) {
                continue;
            }
            // TEAM/ALL: trust people-service list (already TEAM/ALL for employees:view)
            if (employeeId != null && !employeeId.equals(id)) {
                continue;
            }
            if (locationId != null && !locationId.equals(asLong(e.get("locationId")))) {
                continue;
            }
            if (departmentId != null && !departmentId.equals(asLong(e.get("departmentId")))) {
                continue;
            }
            if (teamId != null && !teamId.equals(asLong(e.get("teamId")))) {
                continue;
            }
            // Active employment preferred for operational reports
            if (e.has("active") && e.get("active").isBoolean() && !e.get("active").asBoolean()) {
                // still include for master/headcount; callers can filter
            }
            out.add(e);
        }
        if (employeeId != null && out.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Forbidden: employee outside reports scope");
        }
        out.sort((a, b) -> String.valueOf(text(a, "fullName")).compareToIgnoreCase(String.valueOf(text(b, "fullName"))));
        return out;
    }

    public static Long asLong(JsonNode n) {
        if (n == null || n.isNull()) {
            return null;
        }
        if (n.isNumber()) {
            return n.asLong();
        }
        String s = n.asText("").trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) {
            return null;
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String text(JsonNode n, String field) {
        if (n == null || !n.has(field) || n.get(field).isNull()) {
            return "";
        }
        return n.get(field).asText("");
    }

    public static boolean isActive(JsonNode e) {
        if (e == null) {
            return false;
        }
        if (e.has("active") && e.get("active").isBoolean()) {
            return e.get("active").asBoolean();
        }
        String st = text(e, "status").toLowerCase(Locale.ROOT).trim();
        if (st.isBlank()) {
            return true;
        }
        // "On leave" is still employed; resigned/terminated/inactive are not.
        return "active".equals(st) || "on leave".equals(st) || "onleave".equals(st.replace(" ", ""));
    }
}
