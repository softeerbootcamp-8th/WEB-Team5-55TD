package com.ootd.pickup.consignments.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ootd.pickup.consignments.domain.Consignment;

public interface ConsignmentJpaRepository extends JpaRepository<Consignment, Long> {
    Optional<Consignment> findConsignmentByConsignmentId(Long consignmentId);
}
