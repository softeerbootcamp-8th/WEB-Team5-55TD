package com.ootd.pickup.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException.Reference;

class JacksonFieldPathResolverTest {

  @Test
  void 필드명만_있으면_점으로_이어붙인다() {
    // given
    List<Reference> path =
        List.of(new Reference(Object.class, "certificate"), new Reference(Object.class, "grade"));

    // when
    String result = JacksonFieldPathResolver.formatPath(path);

    // then
    assertThat(result).isEqualTo("certificate.grade");
  }

  @Test
  void 배열_인덱스가_섞여있으면_대괄호로_표현한다() {
    // given
    List<Reference> path =
        List.of(
            new Reference(Object.class, "images"),
            new Reference(Object.class, 1),
            new Reference(Object.class, "consignmentImageId"));

    // when
    String result = JacksonFieldPathResolver.formatPath(path);

    // then
    assertThat(result).isEqualTo("images[1].consignmentImageId");
  }

  @Test
  void 경로가_비어있으면_null을_반환한다() {
    // when
    String result = JacksonFieldPathResolver.formatPath(List.of());

    // then
    assertThat(result).isNull();
  }

  @Test
  void Jackson_예외가_원인_체인에_없으면_null을_반환한다() {
    // given
    RuntimeException exception = new RuntimeException("본문 자체가 JSON이 아님");

    // when
    String result = JacksonFieldPathResolver.resolve(exception);

    // then
    assertThat(result).isNull();
  }
}
