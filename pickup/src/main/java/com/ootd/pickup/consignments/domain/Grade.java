package com.ootd.pickup.consignments.domain;

import com.ootd.pickup.global.exception.PickUpException;
import lombok.Getter;

import java.util.Arrays;

import static com.ootd.pickup.global.exception.ExceptionCode.INVALID_GRADE;

@Getter
public enum Grade {
    GEM_MINT(10, "Gem Mint"),
    MINT(9, "Mint"),
    NM_MT(8, "NM-MT"),
    NM(7, "Near Mint"),
    EX_MT(6, "EX-MT"),
    EX(5, "Excellent"),
    VG_EX(4, "VG-EX"),
    VG(3, "Very Good"),
    GOOD(2, "Good"),
    POOR(1, "Poor");

    private final int score;
    private final String displayName;

    Grade(int score, String displayName) {
        this.score = score;
        this.displayName = displayName;
    }

    public static Grade from(String grade) {
        if (grade == null || grade.isBlank()) {
            return null;
        }

        return Arrays.stream(values())
                .filter(value -> value.name().equalsIgnoreCase(grade)
                        || String.valueOf(value.score).equals(grade))
                .findFirst()
                .orElseThrow(() -> new PickUpException(INVALID_GRADE));
    }
}