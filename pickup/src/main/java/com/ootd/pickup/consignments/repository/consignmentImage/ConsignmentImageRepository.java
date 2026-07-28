package com.ootd.pickup.consignments.repository.consignmentImage;

import java.util.List;

import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.ConsignmentImage;

public interface ConsignmentImageRepository {
    List<ConsignmentImage> saveAll(List<ConsignmentImage> consignmentImages);

    List<ConsignmentImage> findAllByConsignmentOrderByImageOrderAsc(Consignment consignment);
}
