package com.connecthub.gateway.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {

    private JwtAuthenticationFilter filter;
    private GatewayFilterChain chain;
    private ReactiveValueOperations<String, String> ops;
    private final String rawSecret = "fUfwkEaeomu7puBOIl0ftR50UPF4CBPUDxZ0lcaGXL2hY3Zai4DS4YO7l4902IJsKVdHyNjdpCL4LX7IGkw2qg==";

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ReactiveStringRedisTemplate redisTemplate = mock(ReactiveStringRedisTemplate.class);
        ops = mock(ReactiveValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);

        // opsForValue().get() — used for tier override lookup ("sub:tier:{userId}")
        when(ops.get(anyString())).thenReturn(Mono.empty());

        // hasKey() — used for jti blacklist, user-invalidated, and user-suspended checks.
        // Must return Mono<Boolean> (not null) so Mono.zip() can complete.
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(false));

        filter = new JwtAuthenticationFilter(redisTemplate);
        ReflectionTestUtils.setField(filter, "jwtSecret", rawSecret);
        ReflectionTestUtils.setField(filter, "internalSecret", "gw-internal-secret");
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
    }

    private String generateToken(String sub, String role, String tier) {
        SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(rawSecret));
        return Jwts.builder()
                .subject(sub)
                .claim("email", "test@test.com")
                .claim("username", "testuser")
                .claim("role", role)
                .claim("subscriptionTier", tier)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 100000))
                .signWith(key)
                .compact();
    }

    @Test
    void filter_openEndpoint_bypassesAuth() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/auth/login").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
    }

    @Test
    void filter_missingAuthHeader_returns401() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/users/profile").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verify(chain, never()).filter(any());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void filter_invalidAuthHeaderFormat_returns401() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/users/profile")
                .header(HttpHeaders.AUTHORIZATION, "Basic token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verify(chain, never()).filter(any());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void filter_validToken_mutatesRequestAndForwards() {
        String token = generateToken("123", "USER", "PRO");
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/users/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verify(chain).filter(argThat(ex -> {
            HttpHeaders headers = ex.getRequest().getHeaders();
            return "123".equals(headers.getFirst("X-User-Id")) &&
                   "USER".equals(headers.getFirst("X-User-Role")) &&
                   "PRO".equals(headers.getFirst("X-Subscription-Tier"));
        }));
    }

    private MockServerWebExchange exchangeFor(String token) {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/users/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).build());
    }

    @Test
    void filter_tokenIssuedBeforeInvalidation_returns401() {
        String token = generateToken("123", "USER", "FREE");
        when(ops.get("user:invalidated:123"))
                .thenReturn(Mono.just(String.valueOf(System.currentTimeMillis() + 60_000)));
        MockServerWebExchange exchange = exchangeFor(token);

        filter.filter(exchange, chain).block();

        verify(chain, never()).filter(any());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void filter_tokenIssuedAfterInvalidation_forwards() {
        String token = generateToken("123", "USER", "FREE");
        when(ops.get("user:invalidated:123"))
                .thenReturn(Mono.just(String.valueOf(System.currentTimeMillis() - 3_600_000)));

        filter.filter(exchangeFor(token), chain).block();

        verify(chain).filter(any());
    }

    @Test
    void filter_mediaFileBytes_passThroughWithoutJwt_butOtherMediaRoutesStillNeedOne() {
        // file bytes are authorized by media-service via its signed cookie (<img> can't send a JWT)
        MockServerWebExchange file = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/media/file/abc").build());
        filter.filter(file, chain).block();
        verify(chain).filter(any());

        MockServerWebExchange upload = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/media/upload").build());
        filter.filter(upload, chain).block();
        assertEquals(HttpStatus.UNAUTHORIZED, upload.getResponse().getStatusCode());
        verify(chain, times(1)).filter(any());
    }

    @Test
    void filter_stripsInternalServiceHeaderFromExternalRequests() {
        String token = generateToken("123", "USER", "FREE");
        MockServerWebExchange ex = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/users/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("X-Internal-Service", "websocket-service").build());
        filter.filter(ex, chain).block();
        verify(chain).filter(argThat(e -> e.getRequest().getHeaders().getFirst("X-Internal-Service") == null));
    }

    @Test
    void filter_addsInternalSecretOnEveryRoutedRequest_overridingAnyClientValue() {
        // authenticated route, with a forged client-supplied secret
        String token = generateToken("123", "USER", "FREE");
        MockServerWebExchange authed = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/users/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("X-Internal-Auth", "forged-by-client").build());
        filter.filter(authed, chain).block();
        verify(chain).filter(argThat(e -> java.util.List.of("gw-internal-secret")
                .equals(e.getRequest().getHeaders().get("X-Internal-Auth"))));

        // public shortcut route (no JWT) must carry it too, and never the client's value
        MockServerWebExchange open = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/auth/login")
                .header("X-Internal-Auth", "forged-by-client").build());
        filter.filter(open, chain).block();
        verify(chain, times(2)).filter(argThat(e -> java.util.List.of("gw-internal-secret")
                .equals(e.getRequest().getHeaders().get("X-Internal-Auth"))));
    }

    @Test
    void filter_failsFastWithoutInternalSecret() {
        JwtAuthenticationFilter bare = new JwtAuthenticationFilter(mock(ReactiveStringRedisTemplate.class));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> ReflectionTestUtils.invokeMethod(bare, "requireInternalSecret"));
    }

    @Test
    void filter_invalidToken_returns401() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/users/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token.abcd.efgh")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verify(chain, never()).filter(any());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void filter_adminRouteWithAdminRole_forwards() {
        String token = generateToken("999", "ADMIN", "FREE");
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/auth/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verify(chain).filter(argThat(ex -> "ADMIN".equals(ex.getRequest().getHeaders().getFirst("X-User-Role"))));
    }

    @Test
    void filter_adminRouteWithUserRole_returns403() {
        String token = generateToken("123", "USER", "PRO");
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/auth/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verify(chain, never()).filter(any());
        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    }

    @Test
    void filter_defaultTierToFreeIfMissing() {
        String token = generateToken("123", "USER", null); // missing tier
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/users/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verify(chain).filter(argThat(ex -> "FREE".equals(ex.getRequest().getHeaders().getFirst("X-Subscription-Tier"))));
    }

    @Test
    void getOrder_returnsMinus1() {
        assertEquals(-1, filter.getOrder());
    }
}
