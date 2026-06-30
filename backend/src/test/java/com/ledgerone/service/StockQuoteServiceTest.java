package com.ledgerone.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class StockQuoteServiceTest {
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void quoteParsesYahooChartResponseAndCachesBySymbol() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v8/finance/chart/AAPL", exchange -> {
            hits.incrementAndGet();
            byte[] response = """
                    {
                      "chart": {
                        "result": [{
                          "meta": {
                            "regularMarketPrice": 281.63,
                            "regularMarketTime": 1782763201
                          }
                        }],
                        "error": null
                      }
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        int port = server.getAddress().getPort();
        StockQuoteService service = new StockQuoteService(
                new MarketQuoteProperties(
                        true,
                        60,
                        "http://127.0.0.1:" + port + "/v8/finance/chart/{symbol}?interval=1m&range=1d"),
                new ObjectMapper());

        MarketQuote first = service.quote("aapl");
        MarketQuote second = service.quote("AAPL");

        assertThat(first.price()).isEqualByComparingTo("281.6300");
        assertThat(first.observedAt()).isEqualTo(Instant.ofEpochSecond(1782763201));
        assertThat(first.source()).isEqualTo("Yahoo Finance chart");
        assertThat(second).isEqualTo(first);
        assertThat(hits).hasValue(1);
    }

    @Test
    void quoteParsesNasdaqPublicQuoteResponse() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/quote/AAPL/info", exchange -> {
            byte[] response = """
                    {
                      "data": {
                        "symbol": "AAPL",
                        "primaryData": {
                          "lastSalePrice": "$285.52",
                          "lastTradeTimestamp": "Jun 30, 2026 10:50 AM ET"
                        }
                      }
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        int port = server.getAddress().getPort();
        StockQuoteService service = new StockQuoteService(
                new MarketQuoteProperties(true, 60, "http://127.0.0.1:" + port + "/api/quote/{symbol}/info?assetclass=stocks"),
                new ObjectMapper());

        MarketQuote quote = service.quote("AAPL");

        assertThat(quote.price()).isEqualByComparingTo("285.5200");
        assertThat(quote.source()).isEqualTo("Nasdaq public quote");
    }
}
