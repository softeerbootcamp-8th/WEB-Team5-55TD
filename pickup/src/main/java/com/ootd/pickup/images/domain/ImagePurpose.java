package com.ootd.pickup.images.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ImagePurpose {
  CONSIGNMENT("consignments"),
  PROFILE("profiles");

  private final String directory;
}
