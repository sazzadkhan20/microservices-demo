package com.platform.hrms.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Config row describing how a custom field is stored/filtered.
 * Prototype starts empty — populate when registering a custom business later.
 */
@Entity
@Table(name = "tenant_field_definition")
public class TenantFieldDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "field_name", nullable = false, unique = true, length = 64)
    private String fieldName;

    /** JSONB or EXTENSION_TABLE */
    @Column(name = "storage_type", nullable = false, length = 32)
    private String storageType;

    @Column(name = "jsonb_path", length = 128)
    private String jsonbPath;

    @Column(name = "extension_table", length = 128)
    private String extensionTable;

    @Column(name = "extension_column", length = 128)
    private String extensionColumn;

    @Column(nullable = false)
    private boolean filterable = true;

    @Column(name = "indexed_flag", nullable = false)
    private boolean indexedFlag = false;

    @Column(name = "required_flag", nullable = false)
    private boolean requiredFlag = false;

    @Column(name = "unique_active", nullable = false)
    private boolean uniqueActive = false;

    @Column(name = "validation_regex", length = 256)
    private String validationRegex;

    public Long getId() {
        return id;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getStorageType() {
        return storageType;
    }

    public void setStorageType(String storageType) {
        this.storageType = storageType;
    }

    public String getJsonbPath() {
        return jsonbPath;
    }

    public void setJsonbPath(String jsonbPath) {
        this.jsonbPath = jsonbPath;
    }

    public String getExtensionTable() {
        return extensionTable;
    }

    public void setExtensionTable(String extensionTable) {
        this.extensionTable = extensionTable;
    }

    public String getExtensionColumn() {
        return extensionColumn;
    }

    public void setExtensionColumn(String extensionColumn) {
        this.extensionColumn = extensionColumn;
    }

    public boolean isFilterable() {
        return filterable;
    }

    public void setFilterable(boolean filterable) {
        this.filterable = filterable;
    }

    public boolean isIndexedFlag() {
        return indexedFlag;
    }

    public void setIndexedFlag(boolean indexedFlag) {
        this.indexedFlag = indexedFlag;
    }

    public boolean isRequiredFlag() {
        return requiredFlag;
    }

    public void setRequiredFlag(boolean requiredFlag) {
        this.requiredFlag = requiredFlag;
    }

    public boolean isUniqueActive() {
        return uniqueActive;
    }

    public void setUniqueActive(boolean uniqueActive) {
        this.uniqueActive = uniqueActive;
    }

    public String getValidationRegex() {
        return validationRegex;
    }

    public void setValidationRegex(String validationRegex) {
        this.validationRegex = validationRegex;
    }
}
