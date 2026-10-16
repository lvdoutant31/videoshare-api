package com.yingliu.api.admin.admin;

import com.yingliu.api.admin.security.AdminPermissionAuthorizer;
import com.yingliu.api.admin.security.AdminSessionRevoker;
import com.yingliu.framework.web.GlobalExceptionHandler;
import com.yingliu.service.admin.AdminAccountService;
import com.yingliu.service.admin.AdminAppAccountBindingService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminAccountPhoneBindingTest {
    private final AdminAccountService admins = mock(AdminAccountService.class);
    private final AdminPermissionAuthorizer authorizer = mock(AdminPermissionAuthorizer.class);
    private final AdminAppAccountBindingService binding = mock(AdminAppAccountBindingService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new AdminAccountController(
            admins, authorizer, mock(AdminSessionRevoker.class), binding))
            .setControllerAdvice(new GlobalExceptionHandler()).build();
    private static final String TARGET = "/api/v1/admin/admins/2/app-account";
    private static final String BODY = "{\"phone\":\"13800138000\",\"password\":\"password123\"}";

    @Test
    void superAdminBindsTheSelectedAdminRatherThanTheirOwnAccount() throws Exception {
        when(authorizer.require("admin:view")).thenReturn(1L);
        when(admins.getRoleCodes(1L)).thenReturn(List.of("SUPER_ADMIN"));
        when(binding.get(2L)).thenReturn(new AdminAppAccountBindingService.BindingView(false, null));
        when(binding.bind(2L, "13800138000", "password123"))
                .thenReturn(new AdminAppAccountBindingService.BindingView(true, "138****8000"));
        mvc.perform(get(TARGET)).andExpect(status().isOk()).andExpect(jsonPath("$.data.bound").value(false));
        mvc.perform(put(TARGET).contentType("application/json").content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.maskedPhone").value("138****8000"));
        verify(binding).bind(2L, "13800138000", "password123");
        verify(binding, never()).bind(eq(1L), anyString(), anyString());
    }

    @Test
    void ordinaryAdminWithAdminMenuPermissionCannotReadOrBindAnotherAccount() throws Exception {
        when(authorizer.require("admin:view")).thenReturn(1L);
        when(admins.getRoleCodes(1L)).thenReturn(List.of("ADMIN"));
        mvc.perform(get(TARGET)).andExpect(status().isForbidden());
        mvc.perform(put(TARGET).contentType("application/json").content(BODY))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        verifyNoInteractions(binding);
    }
}
