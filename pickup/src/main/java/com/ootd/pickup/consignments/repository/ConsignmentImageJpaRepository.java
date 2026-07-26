package com.ootd.pickup.consignments.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ootd.pickup.consignments.domain.ConsignmentImage;

public interface ConsignmentImageJpaRepository extends JpaRepository<ConsignmentImage, Long> {
}
