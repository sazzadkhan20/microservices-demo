package com.platform.hrms.api.rest;

import com.platform.common.api.PageQuery;
import com.platform.common.api.PageResponse;
import com.platform.hrms.application.support.DynamicListSupport;
import com.platform.hrms.domain.Holiday;
import com.platform.hrms.infrastructure.persistence.HolidayRepository;
import com.platform.hrms.security.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/holidays")
@Tag(name = "Holidays", description = "Public and optional holidays per location")
public class HolidayController {

    private final HolidayRepository holidayRepository;

    public HolidayController(HolidayRepository holidayRepository) {
        this.holidayRepository = holidayRepository;
    }

    @GetMapping
    @Operation(summary = "List holidays (paginated, searchable by name; optional year=)")
    public PageResponse<Holiday> list(@RequestParam Map<String, String> params) {
        PageQuery query = PageQuery.fromParams(params);
        String yearStr = params.get("year");
        Integer year = null;
        if (yearStr != null && !yearStr.isBlank()) {
            year = Integer.parseInt(yearStr.trim());
        }
        Integer y = year;
        var all = holidayRepository.findAll().stream()
                .filter(h -> y == null
                        || (h.getStartDate() != null && h.getStartDate().getYear() == y)
                        || (h.getEndDate() != null && h.getEndDate().getYear() == y))
                .toList();
        return DynamicListSupport.page(
                all,
                query,
                h -> DynamicListSupport.containsIgnoreCase(h.getName(), query.q()),
                h -> Map.of(
                        "holidayType", h.getHolidayType() == null ? "" : h.getHolidayType(),
                        "locationId", h.getLocationId() == null ? "" : String.valueOf(h.getLocationId()),
                        "optionalFlag", String.valueOf(h.isOptionalFlag())
                )
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(feature = "org", action = "manage")
    @Operation(summary = "Create holiday")
    public Holiday create(@RequestBody Map<String, Object> body) {
        Holiday h = new Holiday();
        Object loc = body.get("locationId");
        h.setLocationId(loc == null ? 1L : Long.valueOf(String.valueOf(loc)));
        h.setName(String.valueOf(body.get("name")));
        LocalDate start = LocalDate.parse(String.valueOf(body.getOrDefault("startDate", body.get("date"))));
        Object endObj = body.get("endDate");
        LocalDate end = endObj == null || String.valueOf(endObj).isBlank()
                ? start : LocalDate.parse(String.valueOf(endObj));
        h.setStartDate(start);
        h.setEndDate(end);
        Object type = body.get("holidayType");
        if (type == null) type = body.get("type");
        h.setHolidayType(type == null ? "PUBLIC" : String.valueOf(type));
        Object opt = body.get("optionalFlag");
        h.setOptionalFlag(opt instanceof Boolean b && b);
        return holidayRepository.save(h);
    }

    @PutMapping("/{id}")
    @RequiresPermission(feature = "org", action = "manage")
    @Operation(summary = "Update holiday")
    public Holiday update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Holiday h = holidayRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Holiday not found: " + id));
        Object loc = body.get("locationId");
        if (loc != null && !String.valueOf(loc).isBlank()) {
            h.setLocationId(Long.valueOf(String.valueOf(loc)));
        }
        Object name = body.get("name");
        if (name != null && !String.valueOf(name).isBlank()) {
            h.setName(String.valueOf(name).trim());
        }
        Object startObj = body.get("startDate") != null ? body.get("startDate") : body.get("date");
        if (startObj != null && !String.valueOf(startObj).isBlank()) {
            h.setStartDate(LocalDate.parse(String.valueOf(startObj)));
        }
        Object endObj = body.get("endDate");
        if (endObj != null && !String.valueOf(endObj).isBlank()) {
            h.setEndDate(LocalDate.parse(String.valueOf(endObj)));
        } else if (startObj != null && h.getEndDate().isBefore(h.getStartDate())) {
            h.setEndDate(h.getStartDate());
        }
        if (h.getEndDate().isBefore(h.getStartDate())) {
            throw new IllegalArgumentException("endDate must not be before startDate");
        }
        Object type = body.get("holidayType") != null ? body.get("holidayType") : body.get("type");
        if (type != null && !String.valueOf(type).isBlank()) {
            h.setHolidayType(String.valueOf(type).trim());
        }
        if (body.get("optionalFlag") instanceof Boolean b) {
            h.setOptionalFlag(b);
        }
        return holidayRepository.save(h);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission(feature = "org", action = "manage")
    @Operation(summary = "Delete holiday")
    public void delete(@PathVariable Long id) {
        if (!holidayRepository.existsById(id)) {
            throw new IllegalArgumentException("Holiday not found: " + id);
        }
        holidayRepository.deleteById(id);
    }
}
