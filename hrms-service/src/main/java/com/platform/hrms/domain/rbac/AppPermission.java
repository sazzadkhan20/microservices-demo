package com.platform.hrms.domain.rbac;

import jakarta.persistence.*;

@Entity
@Table(name = "app_permission")
public class AppPermission {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 64) private String feature;
    @Column(nullable = false, length = 32) private String action;
    @Column(length = 256) private String description;

    public Long getId() { return id; }
    public String getFeature() { return feature; }
    public void setFeature(String feature) { this.feature = feature; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String key() { return feature + ":" + action; }
}
