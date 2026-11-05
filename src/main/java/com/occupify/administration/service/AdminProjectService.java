package com.occupify.administration.service;

import com.occupify.administration.dto.base.PageResponse;
import com.occupify.administration.dto.request.AdminProjectFilterRequest;
import com.occupify.administration.dto.response.AdminProjectDetailResponse;
import com.occupify.administration.dto.response.AdminProjectResponse;
import java.util.UUID;
import org.springframework.data.domain.Pageable;

public interface AdminProjectService {
    PageResponse<AdminProjectResponse> getProjects(AdminProjectFilterRequest filter, Pageable pageable);
    AdminProjectDetailResponse getProjectDetail(UUID id);
}

