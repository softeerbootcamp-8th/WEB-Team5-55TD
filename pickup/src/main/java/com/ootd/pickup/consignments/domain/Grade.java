package com.ootd.pickup.consignments.domain;

import lombok.Getter;

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
}