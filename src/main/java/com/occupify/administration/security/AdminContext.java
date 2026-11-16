package com.occupify.administration.security;

import com.occupify.administration.enums.UserRole;
import java.util.UUID;

public record AdminContext(UUID adminId, String email, UserRole role) {}
