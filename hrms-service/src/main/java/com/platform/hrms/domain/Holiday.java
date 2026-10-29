package com.platform.hrms.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "holiday")
public class Holiday {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "location_id", nullable = false)
    private Long locationId;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "holiday_type", length = 32)
    private String holidayType = "PUBLIC";

    @Column(name = "optional_flag")
    private boolean optionalFlag = false;

    public Long getId() { return id; }

    public Long getLocationId() { return locationId; }
    public void setLocationId(Long locationId) { this.locationId = locationId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public String getHolidayType() { return holidayType; }
    public void setHolidayType(String holidayType) { this.holidayType = holidayType; }

    public boolean isOptionalFlag() { return optionalFlag; }
    public void setOptionalFlag(boolean optionalFlag) { this.optionalFlag = optionalFlag; }
}
