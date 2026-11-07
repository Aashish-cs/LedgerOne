package com.ledgerone;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ledgerone.dto.AuthDtos;
import com.ledgerone.dto.TradingDtos;
import com.ledgerone.entity.*;
import com.ledgerone.repository.*;
import com.ledgerone.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"ledgerone.market.simulator-enabled=false", "ledgerone.market.quotes.enabled=false"})
@AutoConfigureMockMvc
class TradingPostgresIT {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DB::getJdbcUrl);
        registry.add("spring.datasource.username", DB::getUsername);
        registry.add("spring.datasource.password", DB::getPassword);
    }
    @Autowired AuthenticationService auth;
    @Autowired TradingService trading;
    @Autowired PortfolioService portfolios;
    @Autowired UserAccountRepository users;
    @Autowired PortfolioRepository accounts;
    @Autowired JdbcTemplate sql;
    @Autowired TransactionTemplate transaction;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @LocalServerPort int port;
    @MockitoBean RiskService risk;
    UserAccount user;
    UUID account;
    AuthDtos.AuthResponse session;

    @BeforeEach void account() {
        session = auth.register(new AuthDtos.RegisterRequest(UUID.randomUUID() + "@example.com", "Test12345!", "Integration Test"), "local");
        user = users.findById(session.user().id()).orElseThrow();
        account = portfolios.defaultPortfolio(user).getId();
        transaction.executeWithoutResult(status -> {
            var portfolio = accounts.findById(account).orElseThrow();
            portfolio.setCashBalance(new BigDecimal("1000"));
        });
    }

    TradingDtos.OrderRequest order(String key, OrderSide side, String quantity) {
        return new TradingDtos.OrderRequest(account, "AAPL", side, OrderType.MARKET, new BigDecimal(quantity), null, key);
    }
    TradingDtos.OrderResponse submit(String key, OrderSide side, String quantity) {
        return trading.placeOrder(user.getId(), order(key, side, quantity));
    }
    BigDecimal cash() { return accounts.findById(account).orElseThrow().getCashBalance(); }
    long ledger() { return sql.queryForObject("select count(*) from ledger_transactions where portfolio_id=?", Long.class, account); }
    List<TradingDtos.OrderResponse> race(Supplier<TradingDtos.OrderResponse> first, Supplier<TradingDtos.OrderResponse> second) throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            var gate = new CountDownLatch(1);
            var a = pool.submit(() -> { gate.await(); return first.get(); });
            var b = pool.submit(() -> { gate.await(); return second.get(); });
            gate.countDown();
            return List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        }
    }

    @Test void cleanMigrationsContainFourteenBusinessTables() {
        assertThat(sql.queryForObject("select count(*) from information_schema.tables where table_schema='public' and table_type='BASE TABLE' and table_name<>'flyway_schema_history'", Integer.class)).isEqualTo(14);
        assertThat(sql.queryForObject("select count(*) from flyway_schema_history where success", Integer.class)).isEqualTo(6);
    }
    @Test void simultaneousBuysCannotSpendTheSameCash() throws Exception {
        var results = race(() -> submit("buy-a", OrderSide.BUY, "4"), () -> submit("buy-b", OrderSide.BUY, "4"));
        assertThat(results).extracting(TradingDtos.OrderResponse::status).containsExactlyInAnyOrder(OrderStatus.FILLED, OrderStatus.REJECTED);
        assertThat(cash()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(ledger()).isEqualTo(1);
    }
    @Test void simultaneousSellsCannotSellTheSameShares() throws Exception {
        submit("initial", OrderSide.BUY, "1");
        var results = race(() -> submit("sell-a", OrderSide.SELL, "1"), () -> submit("sell-b", OrderSide.SELL, "1"));
        assertThat(results).extracting(TradingDtos.OrderResponse::status).containsExactlyInAnyOrder(OrderStatus.FILLED, OrderStatus.REJECTED);
        assertThat(sql.queryForObject("select quantity from holdings where portfolio_id=?", BigDecimal.class, account)).isEqualByComparingTo("0");
        assertThat(cash()).isEqualByComparingTo("998");
        assertThat(ledger()).isEqualTo(2);
    }
    @Test void concurrentDuplicateOnlyPostsOneLedgerEntry() throws Exception {
        var results = race(() -> submit("same", OrderSide.BUY, "1"), () -> submit("same", OrderSide.BUY, "1"));
        assertThat(results.get(0).id()).isEqualTo(results.get(1).id());
        assertThat(ledger()).isEqualTo(1);
    }
    @Test void conflictingIdIsRejected() {
        submit("same", OrderSide.BUY, "1");
        assertThatThrownBy(() -> submit("same", OrderSide.BUY, "2")).isInstanceOf(com.ledgerone.exception.ConflictException.class);
        assertThat(ledger()).isEqualTo(1);
    }
    @Test void failureAfterCashUpdateRollsEverythingBack() {
        doThrow(new IllegalStateException("injected failure")).when(risk).evaluateAndPersist(any());
        assertThatThrownBy(() -> submit("rollback", OrderSide.BUY, "1")).isInstanceOf(IllegalStateException.class);
        assertThat(cash()).isEqualByComparingTo("1000");
        assertThat(ledger()).isZero();
        assertThat(sql.queryForObject("select count(*) from trade_orders where portfolio_id=?", Long.class, account)).isZero();
    }
    @Test void negativeQuantityNeverChangesBalance() {
        assertThatThrownBy(() -> submit("negative", OrderSide.BUY, "-1")).isInstanceOf(com.ledgerone.exception.BadRequestException.class);
        assertThat(cash()).isEqualByComparingTo("1000");
    }
    @Test void loginAndRefreshRotationRejectReplay() {
        var loggedIn = auth.login(new AuthDtos.LoginRequest(user.getEmail(), "Test12345!"), "local");
        var rotated = auth.refresh(new AuthDtos.RefreshRequest(loggedIn.refreshToken()));
        assertThat(rotated.accessToken()).isNotBlank();
        assertThatThrownBy(() -> auth.refresh(new AuthDtos.RefreshRequest(loggedIn.refreshToken()))).isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);
    }
    @Test void frozenUserCannotUseJwtOrRefresh() throws Exception {
        transaction.executeWithoutResult(status -> users.findById(user.getId()).orElseThrow().setStatus(AccountStatus.FROZEN));
        mvc.perform(get("/api/account/summary").header("Authorization", "Bearer " + session.accessToken())).andExpect(status().isUnauthorized());
        assertThatThrownBy(() -> auth.refresh(new AuthDtos.RefreshRequest(session.refreshToken()))).isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);
    }
    @Test void anonymousAndUserCannotAccessAdmin() throws Exception {
        mvc.perform(get("/api/account/summary")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + session.accessToken())).andExpect(status().isForbidden());
        mvc.perform(post("/api/orders").header("Authorization", "Bearer " + session.accessToken()).contentType("application/json").content("{}")).andExpect(status().isBadRequest());
    }
    @Test void registrationCreatesExactlyOneFundedAccount() {
        var list = portfolios.list(user);
        assertThat(list).hasSize(1);
        assertThatThrownBy(() -> auth.register(new AuthDtos.RegisterRequest(user.getEmail(), "Test12345!", "Duplicate"), "local"))
                .isInstanceOf(com.ledgerone.exception.ConflictException.class);
    }

    HttpResponse<String> http(String method, String path, String token, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10));
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.header("Content-Type", "application/json");
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        try (var client = HttpClient.newHttpClient()) {
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    @Test void actualServletRequiresAdminForActuator() throws Exception {
        assertThat(http("GET", "/actuator/health", null, null).statusCode()).isEqualTo(401);
        assertThat(http("GET", "/actuator/health", session.accessToken(), null).statusCode()).isEqualTo(403);
        sql.update("insert into user_roles(user_id,role_id) select ?,id from roles where name='ADMIN'", user.getId());
        assertThat(http("GET", "/actuator/health", session.accessToken(), null).statusCode()).isEqualTo(200);
    }

    @Test void actualHealthProbesRemainPublicWithoutDiagnosticDetails() throws Exception {
        for (String path : List.of("/livez", "/readyz")) {
            var response = http("GET", path, null, null);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(json.readTree(response.body()).get("status").asText()).isEqualTo("UP");
            assertThat(json.readTree(response.body()).has("components")).isFalse();
        }
    }

    @Test void actualLoginAndAuthenticatedAccountRequestsStillWork() throws Exception {
        var response = http("POST", "/api/auth/login", null,
                json.writeValueAsString(new AuthDtos.LoginRequest(user.getEmail(), "Test12345!")));
        assertThat(response.statusCode()).isEqualTo(200);
        String access = json.readTree(response.body()).get("data").get("accessToken").asText();
        assertThat(http("GET", "/api/account/summary", access, null).statusCode()).isEqualTo(200);
    }
}
