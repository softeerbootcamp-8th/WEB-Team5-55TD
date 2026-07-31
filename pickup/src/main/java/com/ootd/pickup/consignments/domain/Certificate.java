package com.ootd.pickup.consignments.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Certificate {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "certificate_id", nullable = false)
  private Long certificateId;

  @Column(name = "serial_number", nullable = false, unique = true)
  private String serialNumber;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "consignment_id", nullable = false, unique = true)
  private Consignment consignment;

  @Enumerated(EnumType.STRING)
  @Column(name = "grade", nullable = false)
  private Grade grade;

  @Enumerated(EnumType.STRING)
  @Column(name = "certification_body", nullable = false)
  private CertificationBody certificationBody;

  @Column(name = "inspected_at", nullable = false)
  private LocalDate inspectedAt;

  @Builder
  public Certificate(
      String serialNumber,
      Consignment consignment,
      Grade grade,
      CertificationBody certificationBody,
      LocalDate inspectedAt) {
    this.serialNumber = serialNumber;
    this.consignment = consignment;
    this.grade = grade;
    this.certificationBody = certificationBody;
    this.inspectedAt = inspectedAt;
  }

  public void update(
      String serialNumber,
      CertificationBody certificationBody,
      Grade grade,
      LocalDate inspectedAt) {
    this.serialNumber = serialNumber;
    this.certificationBody = certificationBody;
    this.grade = grade;
    this.inspectedAt = inspectedAt;
  }

  public String getGradeDisplay() {
      return this.certificationBody.name() + " " + this.grade.getScore();
  }
}
