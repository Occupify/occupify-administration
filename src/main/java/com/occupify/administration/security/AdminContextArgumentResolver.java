package com.occupify.administration.security;

import com.occupify.administration.enums.UserRole;
import com.occupify.administration.exception.AdminErrorCode;
import com.occupify.administration.exception.AdminException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Slf4j
@Component
public class AdminContextArgumentResolver implements HandlerMethodArgumentResolver {

    @Value("${app.auth.dev-mock.enabled:false}")
    private boolean devMockEnabled;

    @Value("${app.auth.dev-mock.admin-id:99999999-0000-1111-2222-333333333333}")
    private String devMockAdminId;

    @Value("${app.auth.dev-mock.admin-email:admin@occupify.app}")
    private String devMockAdminEmail;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(AuthenticatedAdmin.class)
                && AdminContext.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        HttpServletRequest request = (HttpServletRequest) webRequest.getNativeRequest();

        String userIdHeader = request.getHeader("X-User-Id");
        String userRoleHeader = request.getHeader("X-User-Role");
        String userEmailHeader = request.getHeader("X-User-Email");

        if (StringUtils.hasText(userIdHeader)) {
            UUID adminId;
            try {
                adminId = UUID.fromString(userIdHeader.trim());
            } catch (IllegalArgumentException ex) {
                throw new AdminException(AdminErrorCode.INVALID_PARAMETER, "Invalid UUID in X-User-Id header");
            }

            if (!"ADMIN".equalsIgnoreCase(userRoleHeader)) {
                log.warn("[Security] Non-admin role '{}' tried to access admin endpoint", userRoleHeader);
                throw new AdminException(AdminErrorCode.FORBIDDEN);
            }

            String email = StringUtils.hasText(userEmailHeader) ? userEmailHeader.trim() : "";
            return new AdminContext(adminId, email, UserRole.ADMIN);
        }

        if (devMockEnabled) {
            log.warn("[Security] Dev mock enabled. Auto-injecting dev admin: {}", devMockAdminEmail);
            return new AdminContext(UUID.fromString(devMockAdminId), devMockAdminEmail, UserRole.ADMIN);
        }

        throw new AdminException(AdminErrorCode.UNAUTHORIZED);
    }
}

