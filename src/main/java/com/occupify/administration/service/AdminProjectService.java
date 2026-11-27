package com.occupify.administration.service;

import com.occupify.administration.dto.base.PageResponse;
import com.occupify.administration.dto.request.AdminProjectFilterRequest;
import com.occupify.administration.dto.response.AdminProjectDetailResponse;
import com.occupify.administration.dto.response.AdminProjectResponse;
import com.occupify.administration.security.AdminContext;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;

public interface AdminProjectService {
  PageResponse<AdminProjectResponse> getProjects(
      AdminProjectFilterRequest filter, Pageable pageable);

  AdminProjectDetailResponse getProjectDetail(UUID id);

  AdminProjectResponse closeProject(UUID id, @Nullable String reason, AdminContext adminContext);
}
