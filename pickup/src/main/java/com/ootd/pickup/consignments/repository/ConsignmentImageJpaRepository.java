package com.ootd.pickup.consignments.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.ConsignmentImage;

public interface ConsignmentImageJpaRepository extends JpaRepository<ConsignmentImage, Long> {
    List<ConsignmentImage> findAllByConsignmentOrderByImageOrderAsc(Consignment consignment);
}
