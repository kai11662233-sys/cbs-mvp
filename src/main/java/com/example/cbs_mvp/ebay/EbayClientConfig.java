package com.example.cbs_mvp.ebay;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import com.example.cbs_mvp.ops.SystemFlagService;

/**
 * eBay クライアント設定。
 * プロファイルで Stub / Real を切り替える。
 * - stub profile (デフォルト): StubEbayClient, StubEbayOrderClient
 * - real profile: RealEbayClient, RealEbayOrderClient
 */
@Configuration
public class EbayClientConfig {

    /**
     * Stub profile用。既存の StubEbayClient を利用。
     */
    @Bean
    @Profile("!real")
    @Primary
    public EbayClient stubEbayClient(SystemFlagService flags) {
        return new StubEbayClient(flags);
    }

    /**
     * Stub profile用。既存の StubEbayOrderClient を利用。
     */
    @Bean
    @Profile("!real")
    @Primary
    public EbayOrderClient stubEbayOrderClient(SystemFlagService flags) {
        return new StubEbayOrderClient(flags);
    }
}
