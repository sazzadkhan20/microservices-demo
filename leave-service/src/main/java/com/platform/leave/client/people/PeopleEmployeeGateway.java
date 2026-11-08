package com.platform.leave.client.people;

import com.platform.common.people.PeopleDirectoryClient;
import com.platform.leave.client.people.EmployeeView;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Remote People directory — Leave has no local employee table (guide §3).
 */
@Component
public class PeopleEmployeeGateway {

    private final PeopleDirectoryClient people;

    public PeopleEmployeeGateway(PeopleDirectoryClient people) {
        this.people = people;
    }

    public Optional<EmployeeView> findById(Long id) {
        return people.findById(id).map(PeopleEmployeeGateway::fromMap);
    }

    public List<EmployeeView> findAll() {
        return people.findAll().stream().map(PeopleEmployeeGateway::fromMap).toList();
    }

    public List<EmployeeView> findByTeamIdAndActiveTrue(Long teamId) {
        return people.findByTeamIdAndActiveTrue(teamId).stream().map(PeopleEmployeeGateway::fromMap).toList();
    }

    public List<EmployeeView> findByManagerId(Long managerId) {
        return people.findByManagerId(managerId).stream().map(PeopleEmployeeGateway::fromMap).toList();
    }

    public static EmployeeView fromMap(Map<String, Object> m) {
        EmployeeView e = new EmployeeView();
        e.setId(asLong(m.get("id")));
        e.setEmployeeCode(str(m.get("employeeCode")));
        e.setFullName(str(m.get("fullName")));
        e.setEmail(str(m.get("email")));
        e.setDepartmentId(asLong(m.get("departmentId")));
        e.setDesignationId(asLong(m.get("designationId")));
        e.setManagerId(asLong(m.get("managerId")));
        e.setPreferredName(str(m.get("preferredName")));
        e.setJobTitle(str(m.get("jobTitle")));
        e.setLocationId(asLong(m.get("locationId")));
        e.setTeamId(asLong(m.get("teamId")));
        e.setDesignationApproverRole(str(m.get("designationApproverRole")));
        e.setTeamCode(str(m.get("teamCode")));
        e.setTeamMaxOnLeave(asInt(m.get("teamMaxOnLeave")));
        e.setTeamClashAction(str(m.get("teamClashAction")));
        e.setTeamLeadEmployeeId(asLong(m.get("teamLeadEmployeeId")));
        e.setEmploymentType(str(m.get("employmentType")));
        e.setStartDate(asDate(m.get("startDate")));
        e.setProbationEnd(asDate(m.get("probationEnd")));
        e.setContractEnd(asDate(m.get("contractEnd")));
        e.setPayCurrency(str(m.get("payCurrency")));
        e.setPersonalEmail(str(m.get("personalEmail")));
        e.setStatus(str(m.get("status")));
        e.setPhone(str(m.get("phone")));
        e.setWorkPattern(str(m.get("workPattern")));
        e.setBaseSalary(asDecimal(m.get("baseSalary")));
        e.setHourlyRate(asDecimal(m.get("hourlyRate")));
        Object cf = m.get("customFields");
        if (cf instanceof Map<?, ?> map) {
            Map<String, Object> copy = new HashMap<>();
            map.forEach((k, v) -> copy.put(String.valueOf(k), v));
            e.setCustomFields(copy);
        }
        e.setActive(!(m.get("active") instanceof Boolean b) || b);
        e.setCreatedAt(asInstant(m.get("createdAt")));
        e.setUpdatedAt(asInstant(m.get("updatedAt")));
        return e;
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static Long asLong(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() || "null".equalsIgnoreCase(s) ? null : Long.parseLong(s);
    }

    private static Integer asInt(Object v) {
        Long n = asLong(v);
        return n == null ? null : n.intValue();
    }

    private static BigDecimal asDecimal(Object v) {
        if (v == null) {
            return BigDecimal.ZERO;
        }
        if (v instanceof BigDecimal bd) {
            return bd;
        }
        if (v instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        return new BigDecimal(String.valueOf(v));
    }

    private static LocalDate asDate(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof LocalDate d) {
            return d;
        }
        String s = String.valueOf(v).trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) {
            return null;
        }
        return LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s);
    }

    private static Instant asInstant(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Instant i) {
            return i;
        }
        try {
            return Instant.parse(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }
}
