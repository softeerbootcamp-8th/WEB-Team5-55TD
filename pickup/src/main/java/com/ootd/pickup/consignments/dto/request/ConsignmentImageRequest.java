package com.ootd.pickup.consignments.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;

public record ConsignmentImageRequest(Long productImageId, String temporaryObjectKey) {

  public ConsignmentImageRequest(String temporaryObjectKey) {
    this(null, temporaryObjectKey);
  }

  @JsonIgnore
  @AssertTrue(message = "기존 이미지 ID 또는 새 이미지 객체 키 중 하나만 입력해야 합니다.")
  public boolean isValidReference() {
    return (productImageId == null) != (temporaryObjectKey == null || temporaryObjectKey.isBlank());
  }
}
