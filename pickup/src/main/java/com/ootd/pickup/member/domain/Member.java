package com.ootd.pickup.member.domain;

import at.favre.lib.crypto.bcrypt.BCrypt;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

  public static Member create(String loginId, String password, String nickname) {
    Member member = new Member();
    member.loginId = loginId;
    member.password = password;
    member.nickname = nickname;
    member.joinedAt = LocalDateTime.now(ZoneOffset.UTC);
    member.updatedAt = member.joinedAt;
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
}
