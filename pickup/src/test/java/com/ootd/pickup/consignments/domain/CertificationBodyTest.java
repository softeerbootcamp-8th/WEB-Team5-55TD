package com.ootd.pickup.consignments.domain;

import static org.assertj.core.api.Assertions.*;

import com.ootd.pickup.global.exception.PickUpException;
import org.junit.jupiter.api.Test;

class CertificationBodyTest {

  @Test
  void 이름으로_조회하면_대소문자와_무관하게_해당_감정기관을_반환한다() {
    // when
    CertificationBody certificationBody = CertificationBody.from("psa");

    // then
    assertThat(certificationBody).isEqualTo(CertificationBody.PSA);
  }

  @Test
  void 빈_문자열이면_null을_반환한다() {
    // when
    CertificationBody certificationBody = CertificationBody.from(" ");

    // then
    assertThat(certificationBody).isNull();
  }

  @Test
  void 존재하지_않는_감정기관이면_예외가_발생한다() {
    // when & then
    assertThatThrownBy(() -> CertificationBody.from("XYZ")).isInstanceOf(PickUpException.class);
  }
}
