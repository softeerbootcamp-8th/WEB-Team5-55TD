package com.ootd.pickup.member.domain;

import at.favre.lib.crypto.bcrypt.BCrypt;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
// 유니크 제약 이름을 Flyway(V3.1)와 맞춘다. 가입 경합으로 INSERT가 실패했을 때 어느 값이 겹쳤는지
// 제약 이름으로 구분하는데, 이름이 없으면 테스트용 H2 스키마에서는 자동 생성 이름이 붙어 구분할 수 없다.
@Table(
    name = "member",
    uniqueConstraints = {
      @UniqueConstraint(name = "uk_member_login_id", columnNames = "login_id"),
      @UniqueConstraint(name = "uk_member_nickname", columnNames = "nickname")
    })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Member {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long memberId;

  @Column(nullable = true)
  private String loginId;

  @Column(nullable = true)
  private String password;

  @Column(nullable = true)
  private String nickname;

  @Column(nullable = true)
  private LocalDateTime joinedAt;

  @Column(nullable = true)
  private LocalDateTime updatedAt;

  @Column(name = "profile_image_object_key", length = 512)
  private String profileImageObjectKey;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private MemberStatus status;

  @Column(nullable = true)
  private LocalDateTime withdrawnAt;

  public static Member create(String loginId, String password, String nickname) {
    Member member = new Member();
    member.loginId = loginId;
    member.password = password;
    member.nickname = nickname;
    member.joinedAt = LocalDateTime.now(ZoneOffset.UTC);
    member.updatedAt = member.joinedAt;
    member.status = MemberStatus.ACTIVE;
    return member;
  }

  public void updateProfile(String nickname, String passwordHash) {
    if (nickname != null) {
      this.nickname = nickname;
    }
    if (passwordHash != null) {
      this.password = passwordHash;
    }
    updatedAt = LocalDateTime.now(ZoneOffset.UTC);
  }

  public void updateProfileImage(String profileImageObjectKey) {
    this.profileImageObjectKey = profileImageObjectKey;
    updatedAt = LocalDateTime.now(ZoneOffset.UTC);
  }

  public void removeProfileImage() {
    profileImageObjectKey = null;
    updatedAt = LocalDateTime.now(ZoneOffset.UTC);
  }

  public boolean isPasswordMatched(String rawPassword) {
    if (password == null) {
      return false;
    }

    return BCrypt.verifyer().verify(rawPassword.toCharArray(), password).verified;
  }

  public boolean isWithdrawn() {
    return status == MemberStatus.WITHDRAWN;
  }

  /**
   * 탈퇴 처리한다. 로그인 아이디와 비밀번호를 지워 재로그인을 막고, 유니크 제약을 비워 같은 아이디로 재가입할 수 있게 한다. 닉네임은 기존 입찰/상품 내역에 계속
   * 노출되므로 남겨 둔다.
   */
  public void withdraw() {
    status = MemberStatus.WITHDRAWN;
    withdrawnAt = LocalDateTime.now();
    loginId = null;
    password = null;
    updatedAt = withdrawnAt;
  }
}
