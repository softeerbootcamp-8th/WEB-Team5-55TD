package com.ootd.pickup.consignments.dto.request;

import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.ConsignmentImage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.stream.IntStream;

public record RegisterConsignmentRequest(
    @NotNull Long cardId,
    String majorDefect,
    @NotNull @Valid CertificateRequest certificate,
    @NotNull @Size(min = 2) @Valid List<ConsignmentImageRequest> images) {
  public List<ConsignmentImage> toConsignmentImages(Consignment consignment) {
    return IntStream.range(0, images.size())
        .mapToObj(index -> images.get(index).toEntity(consignment, index + 1))
        .toList();
  }
}
