package com.ootd.pickup.consignments.repository.consignmentImage;

import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.ConsignmentImage;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

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
