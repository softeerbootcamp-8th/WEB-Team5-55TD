package com.ootd.pickup.consignments.domain;

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
}