package com.ootd.pickup.auction.cache;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AuctionSnapshotCacheProperties.class)
public class AuctionSnapshotCacheConfig {}
