package com.ootd.pickup.member.domain;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.ootd.pickup.images.service.ImageUrlResolver;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

  @Column(name = "profile_image_object_key", length = 512)
  private String profileImageObjectKey;

  @Column(length = 32)
  private String oauthProvider;

  private String oauthSubject;

  @Column(length = 2048)
  private String externalProfileImageUrl;

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

  public static Member createOAuth(
      String provider, String subject, String nickname, String profileImageUrl) {
    Member member = create(provider.toLowerCase() + "_" + subject, null, nickname);
    member.oauthProvider = provider;
    member.oauthSubject = subject;
    member.externalProfileImageUrl = profileImageUrl;
    return member;
  }

  public void setExternalProfileImageUrl(String profileImageUrl) {
    this.externalProfileImageUrl = profileImageUrl;
  }

  public String getResolvedProfileImageUrl(ImageUrlResolver imageUrlResolver) {
    return externalProfileImageUrl != null
        ? externalProfileImageUrl
        : imageUrlResolver.resolve(profileImageObjectKey);
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
    this.externalProfileImageUrl = null;
    updatedAt = LocalDateTime.now(ZoneOffset.UTC);
  }

  public void removeProfileImage() {
    profileImageObjectKey = null;
    externalProfileImageUrl = null;
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
   * 탈퇴 처리한다. 로그인 아이디, 비밀번호, OAuth 연결 정보를 지워 기존 계정의 재로그인을 막고 같은 인증 정보로 신규 가입할 수 있게 한다. 닉네임은 기존 입찰/상품
   * 내역에 계속 노출되므로 남겨 둔다.
   */
  public void withdraw() {
    status = MemberStatus.WITHDRAWN;
    withdrawnAt = LocalDateTime.now(ZoneOffset.UTC);
    loginId = null;
    password = null;
    clearOAuthIdentity();
    updatedAt = withdrawnAt;
  }

  /** 탈퇴한 소셜 회원이 같은 소셜 계정으로 새로 가입할 수 있도록 기존 OAuth 연결을 해제한다. */
  public void clearOAuthIdentity() {
    oauthProvider = null;
    oauthSubject = null;
    externalProfileImageUrl = null;
  }
}
