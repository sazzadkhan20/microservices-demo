package com.platform.hrms.application;

import com.platform.hrms.extension.config.TenantConfigLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmployeeRulesService — config-driven FR-EMP rules")
class EmployeeRulesServiceTest {

    @Mock TenantConfigLoader configLoader;
    @InjectMocks EmployeeRulesService service;

    @Test
    void workEmailMustMatchConfiguredDomainOrAlias() {
        when(configLoader.loadSettingsMap()).thenReturn(Map.of(
                "employeeRules", Map.of(
                        "workEmailDomain", "qiicreative.com.au",
                        "workEmailDomainAliases", List.of("qiicreative.local")
                )
        ));
        EmployeeRulesService.Rules rules = service.load();
        assertDoesNotThrow(() -> service.assertWorkEmail("hr@qiicreative.com.au", rules));
        assertDoesNotThrow(() -> service.assertWorkEmail("asman@qiicreative.local", rules));
        assertThrows(IllegalArgumentException.class,
                () -> service.assertWorkEmail("hr@gmail.com", rules));
    }

    @Test
    void contractTypesRequireEndFromConfig() {
        when(configLoader.loadSettingsMap()).thenReturn(Map.of(
                "employeeRules", Map.of(
                        "contractTypesRequiringEnd", List.of("CONTRACT", "INTERN")
                )
        ));
        EmployeeRulesService.Rules rules = service.load();
        assertTrue(service.requiresContractEnd("CONTRACT", rules));
        assertTrue(service.requiresContractEnd("intern", rules));
        assertFalse(service.requiresContractEnd("FULL_TIME", rules));
    }

    @Test
    void statusesAndOffboardingComeFromConfigDefaults() {
        when(configLoader.loadSettingsMap()).thenReturn(Map.of());
        EmployeeRulesService.Rules rules = service.load();
        assertDoesNotThrow(() -> service.assertStatusAllowed("Active", rules));
        assertDoesNotThrow(() -> service.assertStatusAllowed("On leave", rules));
        assertThrows(IllegalArgumentException.class, () -> service.assertStatusAllowed("Ghost", rules));
        assertTrue(service.isOffboarding("Resigned", rules));
        assertTrue(service.isInactive("Terminated", rules));
        assertFalse(service.isOffboarding("Active", rules));
    }

    @Test
    void documentTypesAreValidated() {
        when(configLoader.loadSettingsMap()).thenReturn(Map.of());
        EmployeeRulesService.Rules rules = service.load();
        assertDoesNotThrow(() -> service.assertDocumentType("NDA", rules));
        assertThrows(IllegalArgumentException.class, () -> service.assertDocumentType("Passport scan", rules));
    }
}
