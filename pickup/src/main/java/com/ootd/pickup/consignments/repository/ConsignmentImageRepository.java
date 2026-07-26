package com.ootd.pickup.consignments.repository;

import java.util.List;

import com.ootd.pickup.consignments.domain.ConsignmentImage;

public interface ConsignmentImageRepository {
    List<ConsignmentImage> saveAll(List<ConsignmentImage> consignmentImages);
}
