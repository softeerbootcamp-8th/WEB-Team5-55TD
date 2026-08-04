package com.ootd.pickup.admin.repository;

import com.ootd.pickup.admin.domain.Admin;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminJpaRepository extends JpaRepository<Admin, Long> {

  Optional<Admin> findByLoginId(String loginId);
}
