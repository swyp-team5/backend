package com.autoschedule.auth.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.autoschedule.auth.config.SocialAuthProperties;
import com.autoschedule.global.exception.ApiException;
import com.autoschedule.member.domain.SocialProvider;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Apple 소셜 인증 전략이 identity token과 authorization code를 실제 검증 흐름으로 처리하는지 검증한다.
 */
class AppleSocialAuthProviderTest {

    private static final String CLIENT_ID = "com.autoschedule.ios";
    private static final String TEAM_ID = "TEAMID1234";
    private static final String KEY_ID = "APPLEKEYID";
    private static final String APPLE_SUBJECT = "apple-subject";
    private static final String EMAIL = "apple@test.com";

    private HttpServer httpServer;
    private RSAKey appleJwk;
    private String baseUrl;
    private AtomicReference<Map<String, String>> tokenEndpointForm;
    private AtomicReference<String> tokenEndpointSubject;

    /**
     * Apple JWKS와 token endpoint를 대체할 로컬 HTTP 서버를 준비한다.
     */
    @BeforeEach
    void setUp() throws Exception {
        KeyPair rsaKeyPair = createKeyPair("RSA");
        appleJwk = new RSAKey.Builder((RSAPublicKey) rsaKeyPair.getPublic())
                .privateKey((RSAPrivateKey) rsaKeyPair.getPrivate())
                .keyID("apple-rsa-key")
                .algorithm(JWSAlgorithm.RS256)
                .build();
        tokenEndpointForm = new AtomicReference<>();
        tokenEndpointSubject = new AtomicReference<>(APPLE_SUBJECT);

        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/auth/keys", this::handleJwks);
        httpServer.createContext("/auth/token", this::handleToken);
        httpServer.setExecutor(Executors.newSingleThreadExecutor());
        httpServer.start();
        baseUrl = "http://localhost:" + httpServer.getAddress().getPort();
    }

    /**
     * 테스트용 HTTP 서버를 종료한다.
     */
    @AfterEach
    void tearDown() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }

    /**
     * Apple identity token 서명과 claim을 검증하고 authorizationCode를 token endpoint로 교환한다.
     */
    @Test
    void authenticateVerifiesIdentityTokenAndAuthorizationCode() throws Exception {
        ECPrivateKey appleClientPrivateKey = (ECPrivateKey) createKeyPair("EC").getPrivate();
        AppleSocialAuthProvider provider = createProvider(appleClientPrivateKey);

        SocialUserInfo userInfo = provider.authenticate(new SocialAuthCommand(
                SocialProvider.APPLE,
                createAppleIdentityToken(APPLE_SUBJECT, EMAIL),
                null,
                "authorization-code"
        ));

        assertThat(userInfo.provider()).isEqualTo(SocialProvider.APPLE);
        assertThat(userInfo.subject()).isEqualTo(APPLE_SUBJECT);
        assertThat(userInfo.email()).isEqualTo(EMAIL);
        assertThat(tokenEndpointForm.get())
                .containsEntry("grant_type", "authorization_code")
                .containsEntry("code", "authorization-code")
                .containsEntry("client_id", CLIENT_ID)
                .containsKey("client_secret");
    }

    /**
     * authorizationCode 교환 결과의 subject가 identity token subject와 다르면 인증을 거절한다.
     */
    @Test
    void authenticateRejectsAuthorizationCodeSubjectMismatch() throws Exception {
        tokenEndpointSubject.set("other-apple-subject");
        ECPrivateKey appleClientPrivateKey = (ECPrivateKey) createKeyPair("EC").getPrivate();
        AppleSocialAuthProvider provider = createProvider(appleClientPrivateKey);

        assertThatThrownBy(() -> provider.authenticate(new SocialAuthCommand(
                SocialProvider.APPLE,
                createAppleIdentityToken(APPLE_SUBJECT, EMAIL),
                null,
                "authorization-code"
        ))).isInstanceOf(ApiException.class);
    }

    /**
     * 테스트용 Apple provider를 생성한다.
     */
    private AppleSocialAuthProvider createProvider(ECPrivateKey appleClientPrivateKey) {
        return new AppleSocialAuthProvider(
                WebClient.builder(),
                new SocialAuthProperties(
                        null,
                        null,
                        new SocialAuthProperties.Apple(
                                CLIENT_ID,
                                TEAM_ID,
                                KEY_ID,
                                toPkcs8Pem(appleClientPrivateKey),
                                baseUrl + "/auth/keys",
                                baseUrl + "/auth/token"
                        )
                )
        );
    }

    /**
     * 테스트용 RSA 또는 EC 키 쌍을 생성한다.
     */
    private KeyPair createKeyPair(String algorithm) throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(algorithm);
        if ("RSA".equals(algorithm)) {
            keyPairGenerator.initialize(2048);
        }
        if ("EC".equals(algorithm)) {
            keyPairGenerator.initialize(new ECGenParameterSpec("secp256r1"));
        }
        return keyPairGenerator.generateKeyPair();
    }

    /**
     * Apple JWKS 응답을 반환한다.
     */
    private void handleJwks(HttpExchange exchange) throws IOException {
        byte[] response = ("{\"keys\":[" + appleJwk.toPublicJWK().toJSONString() + "]}").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    /**
     * authorization code 교환 요청을 기록하고 Apple token endpoint 성공 응답을 반환한다.
     */
    private void handleToken(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        tokenEndpointForm.set(parseForm(body));

        byte[] response = """
                {
                  "access_token": "apple-access-token",
                  "token_type": "Bearer",
                  "expires_in": 3600,
                  "id_token": "%s"
                }
                """.formatted(createAppleIdentityToken(tokenEndpointSubject.get(), EMAIL)).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    /**
     * form-urlencoded 요청 본문을 Map으로 변환한다.
     */
    private Map<String, String> parseForm(String body) {
        return java.util.Arrays.stream(body.split("&"))
                .map(parameter -> parameter.split("=", 2))
                .collect(java.util.stream.Collectors.toMap(
                        parts -> decode(parts[0]),
                        parts -> parts.length > 1 ? decode(parts[1]) : ""
                ));
    }

    /**
     * URL 인코딩된 값을 UTF-8 문자열로 복원한다.
     */
    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    /**
     * 테스트용 Apple identity token을 RS256으로 서명해 생성한다.
     */
    private String createAppleIdentityToken(String subject, String email) {
        try {
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer("https://appleid.apple.com")
                    .audience(CLIENT_ID)
                    .subject(subject)
                    .claim("email", email)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusSeconds(300)))
                    .build();
            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256)
                            .type(JOSEObjectType.JWT)
                            .keyID(appleJwk.getKeyID())
                            .build(),
                    claims
            );
            signedJWT.sign(new RSASSASigner(appleJwk.toPrivateKey()));
            return signedJWT.serialize();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    /**
     * EC private key를 Apple .p8 형식과 같은 PKCS#8 PEM 문자열로 변환한다.
     */
    private String toPkcs8Pem(ECPrivateKey privateKey) {
        String encoded = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
                .encodeToString(privateKey.getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + encoded + "\n-----END PRIVATE KEY-----";
    }
}
