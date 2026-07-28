package com.ootd.pickup.consignments.repository.consignmentImage;

import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.ConsignmentImage;

import java.util.List;

public interface ConsignmentImageRepository {
    List<ConsignmentImage> saveAll(List<ConsignmentImage> consignmentImages);

    List<ConsignmentImage> findAllByConsignmentOrderByImageOrderAsc(Consignment consignment);
}
