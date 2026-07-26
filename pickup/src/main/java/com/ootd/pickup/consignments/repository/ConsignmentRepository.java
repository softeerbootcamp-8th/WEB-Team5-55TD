package com.ootd.pickup.consignments.repository;

import java.util.Optional;

import com.ootd.pickup.consignments.domain.Consignment;

public interface ConsignmentRepository {
    Consignment save(Consignment consignment);

    Optional<Consignment> findConsignmentById(Long consignmentId);
}
