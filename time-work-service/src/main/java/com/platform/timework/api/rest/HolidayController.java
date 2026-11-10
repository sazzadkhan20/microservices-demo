package com.platform.timework.api.rest;

import com.platform.common.api.PageQuery;
import com.platform.common.api.PageResponse;
import com.platform.timework.application.HolidayService;
import com.platform.timework.application.support.DynamicListSupport;
import com.platform.timework.domain.Holiday;
import com.platform.timework.infrastructure.persistence.HolidayRepository;
import com.platform.timework.security.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/holidays")
@Tag(name = "Holidays", description = "Per-location holiday calendar (Public/Company + optional flag)")
public class HolidayController {

    private final HolidayRepository holidayRepository;
    private final HolidayService holidayService;

    public HolidayController(HolidayRepository holidayRepository, HolidayService holidayService) {
        this.holidayRepository = holidayRepository;
        this.holidayService = holidayService;
    }

    @GetMapping
    @RequiresPermission(feature = "holidays", action = "view")
    @Operation(summary = "List holidays (paginated; filters: year, locationId, holidayType, optionalFlag)")
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
    @RequiresPermission(feature = "holidays", action = "create")
    @Operation(summary = "Create holiday for a location")
    public Holiday create(@RequestBody Map<String, Object> body) {
        return holidayService.create(body);
    }

    @PutMapping("/{id}")
    @RequiresPermission(feature = "holidays", action = "edit")
    @Operation(summary = "Update holiday")
    public Holiday update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return holidayService.update(id, body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission(feature = "holidays", action = "delete")
    @Operation(summary = "Delete holiday")
    public void delete(@PathVariable Long id) {
        holidayService.delete(id);
    }

    /**
     * FR-HOL-03 — copy a location calendar (or all) from one year to the next for editing.
     * Body: {fromYear, toYear, locationId?}. Skips duplicates already present in the target year.
     */
    @PostMapping("/copy-year")
    @RequiresPermission(feature = "holidays", action = "create")
    @Operation(summary = "Copy holidays to another year (skip existing duplicates)")
    public Map<String, Object> copyYear(@RequestBody Map<String, Object> body) {
        Object fromObj = body.get("fromYear");
        Object toObj = body.get("toYear");
        if (fromObj == null || toObj == null) {
            throw new IllegalArgumentException("fromYear and toYear are required");
        }
        int fromYear = ((Number) fromObj).intValue();
        int toYear = ((Number) toObj).intValue();
        Object locObj = body.get("locationId");
        Long locationId = locObj == null || String.valueOf(locObj).isBlank()
                ? null : Long.valueOf(String.valueOf(locObj));
        return holidayService.copyYear(fromYear, toYear, locationId);
    }
}
