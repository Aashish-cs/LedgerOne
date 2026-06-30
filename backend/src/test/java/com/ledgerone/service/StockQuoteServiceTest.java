package com.ledgerone.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
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
                        "http://127.0.0.1:" + port + "/v8/finance/chart/{symbol}?interval=1m&range=1d",
                        "",
                        ""),
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
                new MarketQuoteProperties(
                        true,
                        60,
                        "http://127.0.0.1:" + port + "/api/quote/{symbol}/info?assetclass=stocks",
                        "",
                        ""),
                new ObjectMapper());

        MarketQuote quote = service.quote("AAPL");

        assertThat(quote.price()).isEqualByComparingTo("285.5200");
        assertThat(quote.source()).isEqualTo("Nasdaq public quote");
    }

    @Test
    void quoteUsesFinnhubWhenApiKeyIsConfigured() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/quote", exchange -> {
            assertThat(exchange.getRequestURI().getQuery()).contains("symbol=AAPL", "token=test-key");
            byte[] response = """
                    {
                      "c": 287.11,
                      "d": 1.23,
                      "dp": 0.43,
                      "h": 290.10,
                      "l": 280.44,
                      "o": 281.75,
                      "pc": 285.88,
                      "t": 1782839040
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.createContext("/stock/profile2", exchange -> {
            assertThat(exchange.getRequestURI().getQuery()).contains("symbol=AAPL", "token=test-key");
            byte[] response = """
                    {
                      "ticker": "AAPL",
                      "name": "Apple Inc",
                      "finnhubIndustry": "Technology"
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
                new MarketQuoteProperties(true, 60, "http://127.0.0.1:" + port + "/fallback/{symbol}", "test-key", "http://127.0.0.1:" + port),
                new ObjectMapper());

        MarketQuote quote = service.quote("aapl");

        assertThat(quote.price()).isEqualByComparingTo("287.1100");
        assertThat(quote.observedAt()).isEqualTo(Instant.ofEpochSecond(1782839040));
        assertThat(quote.source()).isEqualTo("Finnhub quote");
        assertThat(quote.companyName()).isEqualTo("Apple Inc");
        assertThat(quote.sector()).isEqualTo("Technology");
    }

    @Test
    void searchUsesFinnhubWhenApiKeyIsConfigured() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/search", exchange -> {
            assertThat(exchange.getRequestURI().getQuery()).contains("q=apple", "token=test-key");
            byte[] response = """
                    {
                      "count": 1,
                      "result": [
                        {
                          "description": "APPLE INC",
                          "displaySymbol": "AAPL",
                          "symbol": "AAPL",
                          "type": "Common Stock"
                        }
                      ]
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
                new MarketQuoteProperties(true, 60, "http://127.0.0.1:" + port + "/fallback/{symbol}", "test-key", "http://127.0.0.1:" + port),
                new ObjectMapper());

        List<MarketSearchResult> results = service.search("apple", 10);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().symbol()).isEqualTo("AAPL");
        assertThat(results.getFirst().companyName()).isEqualTo("APPLE INC");
        assertThat(results.getFirst().sector()).isEqualTo("Common Stock");
        assertThat(results.getFirst().exchange()).isEqualTo("Finnhub");
    }
}
