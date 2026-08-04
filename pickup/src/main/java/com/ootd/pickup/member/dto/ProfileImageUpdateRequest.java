package com.ootd.pickup.member.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record ProfileImageUpdateRequest(
    @NotNull ProfileImageAction action, String temporaryObjectKey) {

  @JsonIgnore
  @AssertTrue(message = "프로필 이미지 변경 요청이 올바르지 않습니다.")
  public boolean isValidUpdate() {
    if (action == null) {
      return true;
    }
    return switch (action) {
      case SET -> temporaryObjectKey != null && !temporaryObjectKey.isBlank();
      case REMOVE -> temporaryObjectKey == null;
    };
  }
}
