package com.ootd.pickup.admin.repository;

import com.ootd.pickup.admin.domain.Admin;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AdminDataJpaRepository implements AdminRepository {

  private final AdminJpaRepository adminJpaRepository;

  @Override
  public Optional<Admin> findByLoginId(String loginId) {
    return adminJpaRepository.findByLoginId(loginId);
  }

  @Override
  public Optional<Admin> findById(Long adminId) {
    return adminJpaRepository.findById(adminId);
  }
}
