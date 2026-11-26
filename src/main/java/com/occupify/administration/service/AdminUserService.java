package com.occupify.administration.service;

import com.occupify.administration.dto.base.PageResponse;
import com.occupify.administration.dto.request.AdminUserFilterRequest;
import com.occupify.administration.dto.response.AdminUserDetailResponse;
import com.occupify.administration.dto.response.AdminUserResponse;
import com.occupify.administration.security.AdminContext;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;

public interface AdminUserService {
  PageResponse<AdminUserResponse> getUsers(AdminUserFilterRequest filter, Pageable pageable);

  AdminUserDetailResponse getUserDetail(UUID id);

  void deleteUser(UUID id, @Nullable String reason, AdminContext adminContext);

  AdminUserResponse lockUser(UUID id, @Nullable String reason, AdminContext adminContext);

  AdminUserResponse unlockUser(UUID id, AdminContext adminContext);
}
