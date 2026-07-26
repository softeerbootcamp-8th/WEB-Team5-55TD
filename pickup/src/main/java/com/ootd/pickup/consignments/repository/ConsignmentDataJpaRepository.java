package com.ootd.pickup.consignments.repository;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.ootd.pickup.consignments.domain.Consignment;

import lombok.RequiredArgsConstructor;

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
        return consignmentJpaRepository.findConsignmentByConsignmentId(consignmentId);
    }
}
