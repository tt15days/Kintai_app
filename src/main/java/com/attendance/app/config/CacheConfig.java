package com.attendance.app.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        ConcurrentMapCacheManager delegate = new ConcurrentMapCacheManager("holidays", "systemSettings", "workScheduleClasses", "eventTypes", "pendingSubmissionsCount", "pendingCorrectionsCount");
        // TransactionAwareCacheManagerProxy でラップし、Evict/Put をトランザクションコミット後に遅延させる。
        // コミット前Evict→旧値再キャッシュを防ぐ。読み取りTxの遅延Putによる微小なstale窓は本規模で許容。
        return new TransactionAwareCacheManagerProxy(delegate);
    }
}
