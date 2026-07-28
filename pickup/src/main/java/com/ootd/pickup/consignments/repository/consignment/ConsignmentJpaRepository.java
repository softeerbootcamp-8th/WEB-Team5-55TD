package com.ootd.pickup.consignments.repository.consignment;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ootd.pickup.consignments.domain.Consignment;

public interface ConsignmentJpaRepository extends JpaRepository<Consignment, Long> {
}
