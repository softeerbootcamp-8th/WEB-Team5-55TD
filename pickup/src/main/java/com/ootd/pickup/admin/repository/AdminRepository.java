package com.ootd.pickup.admin.repository;

import com.ootd.pickup.admin.domain.Admin;
import java.util.Optional;

public interface AdminRepository {

  Optional<Admin> findByLoginId(String loginId);

  Optional<Admin> findById(Long adminId);
}
