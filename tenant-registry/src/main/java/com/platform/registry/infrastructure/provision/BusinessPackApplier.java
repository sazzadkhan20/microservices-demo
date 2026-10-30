package com.platform.registry.infrastructure.provision;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies a classpath business pack ({@code business-packs/{id}/pack.json}) onto a tenant HRMS DB.
 * Packs own policy surface (settings, leave_policy, optional field definitions) —
 * people/demo data stays in business seeders.
 */
@Component
public class BusinessPackApplier {

    private static final Logger log = LoggerFactory.getLogger(BusinessPackApplier.class);

    private final ObjectMapper objectMapper;

    public BusinessPackApplier(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> loadPack(String packFolder) {
        String path = "business-packs/" + packFolder + "/pack.json";
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return objectMapper.readValue(in, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load business pack: " + path + " — " + e.getMessage(), e);
        }
    }

    /** Settings + leave_policy + fieldDefinitions from the pack. */
    public void applyPolicySurface(Connection conn, String packFolder) throws Exception {
        Map<String, Object> pack = loadPack(packFolder);
        applySettings(conn, pack);
        applyLeavePolicy(conn, pack);
        applyFieldDefinitions(conn, pack);
        log.info("Applied business pack policy surface: {}", packFolder);
    }

    public void applySettingsOnly(Connection conn, String packFolder) throws Exception {
        applySettings(conn, loadPack(packFolder));
    }

    public void applyLeavePolicyOnly(Connection conn, String packFolder) throws Exception {
        applyLeavePolicy(conn, loadPack(packFolder));
    }

    @SuppressWarnings("unchecked")
    private void applySettings(Connection conn, Map<String, Object> pack) throws Exception {
        Object raw = pack.get("settings");
        if (!(raw instanceof Map<?, ?>)) {
            return;
        }
        Map<String, Object> incoming = new HashMap<>((Map<String, Object>) raw);
        Map<String, Object> merged = new HashMap<>();

        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, settings::text FROM tenant_settings ORDER BY id LIMIT 1")) {
            if (rs.next()) {
                long id = rs.getLong(1);
                String json = rs.getString(2);
                if (json != null && !json.isBlank()) {
                    merged.putAll(objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {}));
                }
                merged.putAll(incoming);
                String out = objectMapper.writeValueAsString(merged);
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE tenant_settings SET settings = ?::jsonb, updated_at = NOW() WHERE id = ?")) {
                    ps.setString(1, out);
                    ps.setLong(2, id);
                    ps.executeUpdate();
                }
                return;
            }
        }

        String out = objectMapper.writeValueAsString(incoming);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO tenant_settings (settings, updated_at) VALUES (?::jsonb, NOW())")) {
            ps.setString(1, out);
            ps.executeUpdate();
        }
    }

    @SuppressWarnings("unchecked")
    private void applyLeavePolicy(Connection conn, Map<String, Object> pack) throws Exception {
        Object raw = pack.get("leavePolicy");
        if (!(raw instanceof Map<?, ?>)) {
            return;
        }
        Map<String, Object> p = (Map<String, Object>) raw;
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("DELETE FROM leave_policy");
        }

        String workingDays = objectMapper.writeValueAsString(p.getOrDefault("workingDays", List.of()));
        String approvalChain = objectMapper.writeValueAsString(p.getOrDefault("approvalChain", List.of()));

        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO leave_policy (
                  annual_days, work_week_days, working_days,
                  union_notice_days, non_union_notice_days,
                  require_coverage_check, approval_chain,
                  notice_mode, default_notice_days, coverage_mode,
                  location_field, shift_field, require_relief_on_coverage_fail,
                  default_leave_type_code, updated_at
                ) VALUES (
                  ?, ?, ?::jsonb,
                  ?, ?,
                  ?, ?::jsonb,
                  ?, ?, ?,
                  ?, ?, ?,
                  ?, NOW()
                )
                """)) {
            ps.setInt(1, intVal(p.get("annualDays"), 12));
            ps.setInt(2, intVal(p.get("workWeekDays"), 5));
            ps.setString(3, workingDays);
            ps.setInt(4, intVal(p.get("unionNoticeDays"), 0));
            ps.setInt(5, intVal(p.get("nonUnionNoticeDays"), 0));
            ps.setBoolean(6, boolVal(p.get("requireCoverageCheck"), false));
            ps.setString(7, approvalChain);
            ps.setString(8, strOr(p.get("noticeMode"), "NONE"));
            ps.setInt(9, intVal(p.get("defaultNoticeDays"), 0));
            ps.setString(10, strOr(p.get("coverageMode"), "NONE"));
            setNullableString(ps, 11, p.get("locationField"));
            setNullableString(ps, 12, p.get("shiftField"));
            ps.setBoolean(13, boolVal(p.get("requireReliefOnCoverageFail"), false));
            setNullableString(ps, 14, p.get("defaultLeaveTypeCode"));
            ps.executeUpdate();
        }
    }

    @SuppressWarnings("unchecked")
    private void applyFieldDefinitions(Connection conn, Map<String, Object> pack) throws Exception {
        Object raw = pack.get("fieldDefinitions");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?>)) {
                continue;
            }
            Map<String, Object> f = (Map<String, Object>) item;
            String name = strOr(f.get("fieldName"), null);
            if (name == null || name.isBlank()) {
                continue;
            }
            // Upsert by field_name
            try (PreparedStatement del = conn.prepareStatement(
                    "DELETE FROM tenant_field_definition WHERE field_name = ?")) {
                del.setString(1, name);
                del.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO tenant_field_definition
                      (field_name, storage_type, jsonb_path, extension_table, extension_column,
                       filterable, indexed_flag, required_flag, unique_active, validation_regex)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                ps.setString(1, name);
                ps.setString(2, strOr(f.get("storageType"), "JSONB"));
                setNullableString(ps, 3, f.get("jsonbPath"));
                setNullableString(ps, 4, f.get("extensionTable"));
                setNullableString(ps, 5, f.get("extensionColumn"));
                ps.setBoolean(6, boolVal(f.get("filterable"), false));
                ps.setBoolean(7, boolVal(f.get("indexedFlag"), false));
                ps.setBoolean(8, boolVal(f.get("requiredFlag"), false));
                ps.setBoolean(9, boolVal(f.get("uniqueActive"), false));
                setNullableString(ps, 10, f.get("validationRegex"));
                ps.executeUpdate();
            }
        }
    }

    private static void setNullableString(PreparedStatement ps, int idx, Object value) throws Exception {
        if (value == null || String.valueOf(value).isBlank() || "null".equalsIgnoreCase(String.valueOf(value))) {
            ps.setNull(idx, Types.VARCHAR);
        } else {
            ps.setString(idx, String.valueOf(value));
        }
    }

    private static int intVal(Object v, int def) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v != null) {
            try {
                return Integer.parseInt(v.toString().trim());
            } catch (NumberFormatException ignored) {
                return def;
            }
        }
        return def;
    }

    private static boolean boolVal(Object v, boolean def) {
        if (v instanceof Boolean b) {
            return b;
        }
        if (v != null) {
            return Boolean.parseBoolean(v.toString().trim());
        }
        return def;
    }

    private static String strOr(Object v, String def) {
        if (v == null) {
            return def;
        }
        String s = String.valueOf(v).trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) {
            return def;
        }
        return s;
    }
}
