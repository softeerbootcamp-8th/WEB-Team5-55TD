package com.ootd.pickup.consignments.repository.consignmentImage;

import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.ConsignmentImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConsignmentImageJpaRepository extends JpaRepository<ConsignmentImage, Long> {
    List<ConsignmentImage> findAllByConsignmentOrderByImageOrderAsc(Consignment consignment);
}
