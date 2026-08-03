package com.ootd.pickup.consignments.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsignmentImage {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "consignment_image_id", nullable = false)
  private Long consignmentImageId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "consignment_id", nullable = false)
  private Consignment consignment;

  @Column(name = "image_order", nullable = false)
  private int imageOrder;

  @Column(name = "object_key", nullable = false, length = 512)
  private String objectKey;

  @Builder
  public ConsignmentImage(Consignment consignment, int imageOrder, String objectKey) {
    this.consignment = consignment;
    this.imageOrder = imageOrder;
    this.objectKey = objectKey;
  }

  public void updateImageOrder(int imageOrder) {
    this.imageOrder = imageOrder;
  }
}
