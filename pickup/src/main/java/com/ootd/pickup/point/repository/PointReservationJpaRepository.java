package com.ootd.pickup.point.repository;

import com.ootd.pickup.point.domain.PointReservation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointReservationJpaRepository extends JpaRepository<PointReservation, Long> {}
