package com.platform.hrms.api.rest;

import com.platform.common.api.PageQuery;
import com.platform.common.api.PageResponse;
import com.platform.hrms.application.support.DynamicListSupport;
import com.platform.hrms.domain.Location;
import com.platform.hrms.infrastructure.persistence.LocationRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
}
