package com.ootd.pickup.consignments.repository.consignmentImage;

import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.ConsignmentImage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsignmentImageJpaRepository extends JpaRepository<ConsignmentImage, Long> {
  List<ConsignmentImage> findAllByConsignmentOrderByImageOrderAsc(Consignment consignment);

  List<ConsignmentImage>
      findAllByConsignment_ConsignmentIdInOrderByConsignment_ConsignmentIdAscImageOrderAsc(
          List<Long> consignmentIds);

  void deleteAllByConsignment(Consignment consignment);
}
