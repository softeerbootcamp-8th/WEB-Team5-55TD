package com.ootd.pickup.consignments.domain;

import static com.ootd.pickup.global.exception.ExceptionCode.*;

import com.ootd.pickup.global.exception.PickUpException;
import java.util.Arrays;
import lombok.Getter;

@Getter
public enum CertificationBody {
  PSA("Professional Sports Authenticator"),
  BGS("Beckett Grading Services"),
  CGC("Certified Guaranty Company"),
  SGC("Sportscard Guaranty Corporation"),
  ACE("Ace Grading");

  private final String fullName;

  CertificationBody(String fullName) {
    this.fullName = fullName;
  }

  public static CertificationBody from(String certificationBody) {
    if (certificationBody == null || certificationBody.isBlank()) {
      return null;
    }

    return Arrays.stream(values())
        .filter(value -> value.name().equalsIgnoreCase(certificationBody))
        .findFirst()
        .orElseThrow(() -> new PickUpException(INVALID_CERTIFICATION_BODY));
  }
}
