package com.ootd.pickup.consignments.domain;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.ootd.pickup.global.exception.PickUpException;

class GradeTest {

    @Test
    void 점수_문자열로_조회하면_해당_등급을_반환한다() {
        // when
        Grade grade = Grade.from("10");

        // then
        assertThat(grade).isEqualTo(Grade.GEM_MINT);
    }

    @Test
    void 등급_이름으로_조회하면_대소문자와_무관하게_해당_등급을_반환한다() {
        // when
        Grade grade = Grade.from("gem_mint");

        // then
        assertThat(grade).isEqualTo(Grade.GEM_MINT);
    }

    @Test
    void 빈_문자열이면_null을_반환한다() {
        // when
        Grade grade = Grade.from("  ");

        // then
        assertThat(grade).isNull();
    }

    @Test
    void 존재하지_않는_등급이면_예외가_발생한다() {
        // when & then
        assertThatThrownBy(() -> Grade.from("S급"))
                .isInstanceOf(PickUpException.class);
    }
}
