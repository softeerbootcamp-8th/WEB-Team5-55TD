package com.ootd.pickup.cards.domain;

public enum Language {
    ENGLISH("영어"),
    JAPANESE("일본어"),
    KOREAN("한국어");

    private final String displayName;

    Language(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
