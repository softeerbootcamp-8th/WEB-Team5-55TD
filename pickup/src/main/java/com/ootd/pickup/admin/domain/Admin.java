package com.ootd.pickup.admin.domain;

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
public class Admin {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long adminId;

  @Column(nullable = false, unique = true)
  private String loginId;

  @Column(nullable = false)
  private String password;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  public static Admin create(String loginId, String password, String name) {
    Admin admin = new Admin();
    admin.loginId = loginId;
    admin.password = password;
    admin.name = name;
    admin.createdAt = LocalDateTime.now();
    return admin;
  }

  public boolean isPasswordMatched(String rawPassword) {
    if (password == null) {
      return false;
    }

    return BCrypt.verifyer().verify(rawPassword.toCharArray(), password).verified;
  }
}
