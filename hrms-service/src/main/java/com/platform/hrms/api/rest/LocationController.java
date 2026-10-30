package com.platform.hrms.api.rest;

import com.platform.common.api.PageQuery;
import com.platform.common.api.PageResponse;
import com.platform.hrms.application.support.DynamicListSupport;
import com.platform.hrms.domain.Location;
import com.platform.hrms.infrastructure.persistence.LocationRepository;
import com.platform.hrms.security.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/locations")
@Tag(name = "Locations", description = "Office / site locations")
public class LocationController {

    private final LocationRepository locationRepository;

    public LocationController(LocationRepository locationRepository) {
        this.locationRepository = locationRepository;
    }

    @GetMapping
    @Operation(summary = "List locations (paginated, searchable)")
    public PageResponse<Location> list(@RequestParam Map<String, String> params) {
        PageQuery query = PageQuery.fromParams(params);
        return DynamicListSupport.page(
                locationRepository.findAll(),
                query,
                loc -> DynamicListSupport.containsIgnoreCase(loc.getName(), query.q())
                        || DynamicListSupport.containsIgnoreCase(loc.getCode(), query.q()),
                loc -> Map.of(
                        "code", loc.getCode() == null ? "" : loc.getCode(),
                        "country", loc.getCountry() == null ? "" : loc.getCountry(),
                        "timeZone", loc.getTimeZone() == null ? "" : loc.getTimeZone(),
                        "defaultCurrency", loc.getDefaultCurrency() == null ? "" : loc.getDefaultCurrency()
                )
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(feature = "hierarchy", action = "edit")
    @Operation(summary = "Create location (org structure — hierarchy.edit)")
    public Location create(@RequestBody Map<String, Object> body) {
        String code = requireText(body, "code");
        if (locationRepository.findAll().stream().anyMatch(l -> code.equalsIgnoreCase(l.getCode()))) {
            throw new IllegalStateException("Location code already exists: " + code);
        }
        Location loc = new Location();
        loc.setCode(code);
        loc.setName(requireText(body, "name"));
        loc.setCountry(optionalText(body, "country"));
        loc.setTimeZone(validTimeZone(requireText(body, "timeZone")));
        loc.setDefaultCurrency(requireText(body, "defaultCurrency").toUpperCase(Locale.ROOT));
        return locationRepository.save(loc);
    }

    @PutMapping("/{id}")
    @RequiresPermission(feature = "hierarchy", action = "edit")
    @Operation(summary = "Update location (org structure — hierarchy.edit)")
    public Location update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Location loc = locationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Location not found: " + id));
        if (body.containsKey("code")) {
            String code = requireText(body, "code");
            if (!code.equals(loc.getCode())) {
                if (locationRepository.findAll().stream()
                        .anyMatch(l -> !l.getId().equals(id) && code.equalsIgnoreCase(l.getCode()))) {
                    throw new IllegalStateException("Location code already exists: " + code);
                }
                loc.setCode(code);
            }
        }
        if (body.containsKey("name")) {
            loc.setName(requireText(body, "name"));
        }
        if (body.containsKey("country")) {
            loc.setCountry(optionalText(body, "country"));
        }
        if (body.containsKey("timeZone")) {
            loc.setTimeZone(validTimeZone(requireText(body, "timeZone")));
        }
        if (body.containsKey("defaultCurrency")) {
            loc.setDefaultCurrency(requireText(body, "defaultCurrency").toUpperCase(Locale.ROOT));
        }
        return locationRepository.save(loc);
    }

    private static String validTimeZone(String tz) {
        try {
            ZoneId.of(tz);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid timeZone: " + tz);
        }
        return tz;
    }

    private static String requireText(Map<String, Object> body, String key) {
        String v = optionalText(body, key);
        if (v == null) {
            throw new IllegalArgumentException(key + " is required");
        }
        return v;
    }

    private static String optionalText(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || String.valueOf(v).isBlank()) {
            return null;
        }
        return String.valueOf(v).trim();
    }
}
