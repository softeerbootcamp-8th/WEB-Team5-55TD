package com.ootd.pickup.member.domain;

import jakarta.persistence.*;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@NoArgsConstructor
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long memberId;

    @Column(nullable = true, unique = true)
    private String loginId;

    @Column(nullable = true)
    private String password;

    @Column(nullable = true, unique = true)
    private String nickname;

    @Column(nullable = true)
    private LocalDateTime joinedAt;

    @Column(nullable = true)
    private LocalDateTime updatedAt;

    @Column(nullable = true)
    private String profileImageUrl;

    public static Member create(String loginId, String password, String nickname) {
        Member member = new Member();
        member.loginId = loginId;
        member.password = password;
        member.nickname = nickname;
        member.joinedAt = LocalDateTime.now();
        member.updatedAt = member.joinedAt;
        return member;
    }

    public Long getMemberId() {
        return memberId;
    }

    public String getLoginId() {
        return loginId;
    }

    public String getNickname() {
        return nickname;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }
}
