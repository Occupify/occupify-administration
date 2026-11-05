package com.occupify.administration.security;

import com.occupify.administration.enums.UserRole;
import com.occupify.administration.exception.AdminErrorCode;
import com.occupify.administration.exception.AdminException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.NativeWebRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminContextArgumentResolverTest {

    private AdminContextArgumentResolver resolver;

    @Mock
    private NativeWebRequest webRequest;

    @Mock
    private HttpServletRequest httpRequest;

    @BeforeEach
    void setUp() {
        resolver = new AdminContextArgumentResolver();
        ReflectionTestUtils.setField(resolver, "devMockEnabled", false);
        ReflectionTestUtils.setField(resolver, "devMockAdminId", "99999999-0000-1111-2222-333333333333");
        ReflectionTestUtils.setField(resolver, "devMockAdminEmail", "admin@occupify.app");
    }

    @Test
    @DisplayName("Should resolve AdminContext successfully when valid ADMIN headers are provided")
    void shouldResolveAdminContext_whenValidHeadersProvided() {
        UUID adminId = UUID.randomUUID();
        when(webRequest.getNativeRequest()).thenReturn(httpRequest);
        when(httpRequest.getHeader("X-User-Id")).thenReturn(adminId.toString());
        when(httpRequest.getHeader("X-User-Role")).thenReturn("ADMIN");
        when(httpRequest.getHeader("X-User-Email")).thenReturn("superadmin@occupify.app");

        Object result = resolver.resolveArgument(mock(MethodParameter.class), null, webRequest, null);

        assertThat(result).isInstanceOf(AdminContext.class);
        AdminContext context = (AdminContext) result;
        assertThat(context.adminId()).isEqualTo(adminId);
        assertThat(context.role()).isEqualTo(UserRole.ADMIN);
        assertThat(context.email()).isEqualTo("superadmin@occupify.app");
    }

    @Test
    @DisplayName("Should throw FORBIDDEN when user role is not ADMIN")
    void shouldThrowForbidden_whenRoleIsNotAdmin() {
        UUID userId = UUID.randomUUID();
        when(webRequest.getNativeRequest()).thenReturn(httpRequest);
        when(httpRequest.getHeader("X-User-Id")).thenReturn(userId.toString());
        when(httpRequest.getHeader("X-User-Role")).thenReturn("USER");

        assertThatThrownBy(() -> resolver.resolveArgument(mock(MethodParameter.class), null, webRequest, null))
                .isInstanceOf(AdminException.class)
                .hasFieldOrPropertyWithValue("code", AdminErrorCode.FORBIDDEN.code());
    }

    @Test
    @DisplayName("Should fallback to mock admin when dev-mock is enabled and headers are missing")
    void shouldFallbackToMockAdmin_whenDevMockEnabled() {
        ReflectionTestUtils.setField(resolver, "devMockEnabled", true);
        when(webRequest.getNativeRequest()).thenReturn(httpRequest);
        when(httpRequest.getHeader("X-User-Id")).thenReturn(null);

        Object result = resolver.resolveArgument(mock(MethodParameter.class), null, webRequest, null);

        assertThat(result).isInstanceOf(AdminContext.class);
        AdminContext context = (AdminContext) result;
        assertThat(context.adminId()).isEqualTo(UUID.fromString("99999999-0000-1111-2222-333333333333"));
        assertThat(context.email()).isEqualTo("admin@occupify.app");
        assertThat(context.role()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    @DisplayName("Should throw UNAUTHORIZED when headers are missing and dev-mock is disabled")
    void shouldThrowUnauthorized_whenHeadersMissingAndDevMockDisabled() {
        when(webRequest.getNativeRequest()).thenReturn(httpRequest);
        when(httpRequest.getHeader("X-User-Id")).thenReturn(null);

        assertThatThrownBy(() -> resolver.resolveArgument(mock(MethodParameter.class), null, webRequest, null))
                .isInstanceOf(AdminException.class)
                .hasFieldOrPropertyWithValue("code", AdminErrorCode.UNAUTHORIZED.code());
    }
}

