package com.platform.payroll.security;

import com.platform.common.security.AuthContext;
import com.platform.payroll.api.rest.PayrollController;
import com.platform.payroll.infrastructure.grpc.HrmsEmployeeClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RbacInterceptorTest {

    private final HrmsEmployeeClient hrms = Mockito.mock(HrmsEmployeeClient.class);
    private final RbacInterceptor interceptor = new RbacInterceptor(hrms);

    @AfterEach
    void clear() {
        AuthContext.clear();
    }

    private HandlerMethod handler(String name, Class<?>... params) throws Exception {
        return new HandlerMethod(Mockito.mock(PayrollController.class), PayrollController.class.getMethod(name, params));
    }

    private HandlerMethod calculate() throws Exception {
        return handler("calculate", com.platform.payroll.api.dto.CalculatePayrollRequest.class);
    }

    @Test
    void noEmployeeIdentityIs401() throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(new MockHttpServletRequest(), res, calculate()));
        assertEquals(401, res.getStatus());
    }

    @Test
    void viewOnlyUserCannotCalculate() throws Exception {
        AuthContext.setEmployeeId(5L);
        Mockito.when(hrms.getPermissions(5L)).thenReturn(Set.of("payroll:view", "payroll:read"));
        MockHttpServletResponse res = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(new MockHttpServletRequest(), res, calculate()));
        assertEquals(403, res.getStatus());
    }

    @Test
    void runUserCanCalculateAndPermissionsAreStashed() throws Exception {
        AuthContext.setEmployeeId(9L);
        Mockito.when(hrms.getPermissions(9L)).thenReturn(Set.of("payroll:run", "payroll:view"));
        MockHttpServletRequest req = new MockHttpServletRequest();
        assertTrue(interceptor.preHandle(req, new MockHttpServletResponse(), calculate()));
        assertTrue(((PermissionSet) req.getAttribute(RbacInterceptor.PERMISSIONS_ATTR)).has("payroll", "run"));
    }

    @Test
    void viewUserCanListButWithoutAnyPayrollPermissionIs403() throws Exception {
        HandlerMethod list = handler("listPayslips", java.util.Map.class, jakarta.servlet.http.HttpServletRequest.class);
        AuthContext.setEmployeeId(5L);
        Mockito.when(hrms.getPermissions(5L)).thenReturn(Set.of("payroll:view"));
        assertTrue(interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), list));

        AuthContext.setEmployeeId(6L);
        Mockito.when(hrms.getPermissions(6L)).thenReturn(Set.of("leave:view"));
        MockHttpServletResponse res = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(new MockHttpServletRequest(), res, list));
        assertEquals(403, res.getStatus());
    }

    @Test
    void hrmsOutageFailsClosed() throws Exception {
        AuthContext.setEmployeeId(5L);
        Mockito.when(hrms.getPermissions(5L)).thenThrow(io.grpc.Status.UNAVAILABLE.asRuntimeException());
        MockHttpServletResponse res = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(new MockHttpServletRequest(), res, calculate()));
        assertEquals(503, res.getStatus());
    }
}
