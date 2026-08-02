package com.ootd.pickup.member.domain;

import at.favre.lib.crypto.bcrypt.BCrypt;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;
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

  @Column(name = "profile_image_object_key", unique = true, length = 512)
  private String profileImageObjectKey;

  public static Member create(String loginId, String password, String nickname) {
    Member member = new Member();
    member.loginId = loginId;
    member.password = password;
    member.nickname = nickname;
    member.joinedAt = LocalDateTime.now();
    member.updatedAt = member.joinedAt;
    return member;
  }

  public void updateProfile(String nickname, String passwordHash) {
    if (nickname != null) {
      this.nickname = nickname;
    }
    if (passwordHash != null) {
      this.password = passwordHash;
    }
    updatedAt = LocalDateTime.now();
  }

  public void updateProfileImage(String profileImageObjectKey) {
    this.profileImageObjectKey = profileImageObjectKey;
    updatedAt = LocalDateTime.now();
  }

  public void removeProfileImage() {
    profileImageObjectKey = null;
    updatedAt = LocalDateTime.now();
  }

  public boolean isPasswordMatched(String rawPassword) {
    if (password == null) {
      return false;
    }

    return BCrypt.verifyer().verify(rawPassword.toCharArray(), password).verified;
  }
}
