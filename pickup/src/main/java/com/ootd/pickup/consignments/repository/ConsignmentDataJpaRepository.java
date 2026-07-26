package com.ootd.pickup.consignments.repository;

import com.ootd.pickup.consignments.domain.Consignment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ConsignmentDataJpaRepository implements ConsignmentRepository {

    private final ConsignmentJpaRepository consignmentJpaRepository;

    @Override
    public Consignment save(Consignment consignment) {
        return consignmentJpaRepository.save(consignment);
    }

    @Override
    public Optional<Consignment> findConsignmentById(Long consignmentId) {
        return consignmentJpaRepository.findById(consignmentId);
    }
}
