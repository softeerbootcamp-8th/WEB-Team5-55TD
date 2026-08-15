package com.ootd.pickup.auction.cache;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("cache.auction-snapshot")
public record AuctionSnapshotCacheProperties(Duration ttl) {}
