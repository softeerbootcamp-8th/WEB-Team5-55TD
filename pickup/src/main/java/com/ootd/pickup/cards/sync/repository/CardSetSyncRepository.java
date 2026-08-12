package com.ootd.pickup.cards.sync.repository;

import com.ootd.pickup.cards.sync.domain.CardSetSync;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardSetSyncRepository extends JpaRepository<CardSetSync, String> {}
