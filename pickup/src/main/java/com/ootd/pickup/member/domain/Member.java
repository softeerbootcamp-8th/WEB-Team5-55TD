package com.ootd.pickup.member.domain;

import at.favre.lib.crypto.bcrypt.BCrypt;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
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

    public boolean isPasswordMatched(String rawPassword) {
        return BCrypt.verifyer()
                .verify(rawPassword.toCharArray(), password)
                .verified;
    }
}
