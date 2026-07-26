package com.ootd.pickup.consignments.repository;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.ConsignmentImage;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class ConsignmentImageDataJpaRepository implements ConsignmentImageRepository {

    private final ConsignmentImageJpaRepository consignmentImageJpaRepository;

    @Override
    public List<ConsignmentImage> saveAll(List<ConsignmentImage> consignmentImages) {
        return consignmentImageJpaRepository.saveAll(consignmentImages);
    }

    @Override
    public List<ConsignmentImage> findAllByConsignmentOrderByImageOrderAsc(Consignment consignment) {
        return consignmentImageJpaRepository.findAllByConsignmentOrderByImageOrderAsc(consignment);
    }
}
