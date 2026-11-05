package com.occupify.administration.service;

import com.occupify.administration.dto.base.PageResponse;
import com.occupify.administration.dto.request.AdminUserFilterRequest;
import com.occupify.administration.dto.response.AdminUserDetailResponse;
import com.occupify.administration.dto.response.AdminUserResponse;
import java.util.UUID;
import org.springframework.data.domain.Pageable;

public interface AdminUserService {
    PageResponse<AdminUserResponse> getUsers(AdminUserFilterRequest filter, Pageable pageable);
    AdminUserDetailResponse getUserDetail(UUID id);
}

