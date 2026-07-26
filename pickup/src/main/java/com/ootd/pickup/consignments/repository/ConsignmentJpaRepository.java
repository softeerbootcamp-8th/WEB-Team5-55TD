package com.ootd.pickup.consignments.repository;

import com.ootd.pickup.consignments.domain.Consignment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsignmentJpaRepository extends JpaRepository<Consignment, Long> {
}
