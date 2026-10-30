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
    /** true = Own/Team/All; false = Yes (org-wide, stored as ALL). */
    @Column(nullable = false) private boolean scoped = true;
    @Column(length = 128) private String label;
    @Column(name = "sort_order", nullable = false) private int sortOrder = 100;
    @Column(name = "matrix_visible", nullable = false) private boolean matrixVisible = true;

    public Long getId() { return id; }
    public String getFeature() { return feature; }
    public void setFeature(String feature) { this.feature = feature; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isScoped() { return scoped; }
    public void setScoped(boolean scoped) { this.scoped = scoped; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public boolean isMatrixVisible() { return matrixVisible; }
    public void setMatrixVisible(boolean matrixVisible) { this.matrixVisible = matrixVisible; }

    public String key() { return feature + ":" + action; }
}
