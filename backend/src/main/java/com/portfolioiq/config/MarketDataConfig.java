package com.portfolioiq.config;

import com.portfolioiq.market.FinnhubMarketDataProvider;
import com.portfolioiq.market.MarketDataProvider;
import com.portfolioiq.market.SimulatedMarketDataProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

import java.time.Clock;

/** Chooses the market data source: real if MARKET_DATA_API_KEY is set, otherwise labelled simulated data. */
@Configuration
@EnableScheduling
public class MarketDataConfig {
    private static final Logger log = LoggerFactory.getLogger(MarketDataConfig.class);

    @Bean
    MarketDataProvider marketDataProvider(@Value("${app.market.api-key:}") String apiKey,
                                          @Value("${app.market.base-url:https://finnhub.io/api/v1}") String baseUrl,
                                          Clock clock, RestClient.Builder builder) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("MARKET_DATA_API_KEY is not set: serving clearly labelled SIMULATED market data (development only)");
            return new SimulatedMarketDataProvider(clock);
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        RestClient client = builder.baseUrl(baseUrl).requestFactory(factory)
                .defaultHeader("X-Finnhub-Token", apiKey).build();
        log.info("Using Finnhub market data");
        return new FinnhubMarketDataProvider(client, clock);
    }
}
