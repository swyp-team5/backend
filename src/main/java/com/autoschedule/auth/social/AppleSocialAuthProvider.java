package com.autoschedule.auth.social;

import com.autoschedule.auth.config.SocialAuthProperties;
import com.autoschedule.global.exception.ApiException;
import com.autoschedule.global.exception.ErrorCode;
import com.autoschedule.member.domain.SocialProvider;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.text.ParseException;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Apple identity token 서명과 authorization code를 검증한다.
 */
@Component
public class AppleSocialAuthProvider implements SocialAuthProvider {

    private static final String APPLE_ISSUER = "https://appleid.apple.com";
    private static final String APPLE_CLIENT_SECRET_AUDIENCE = "https://appleid.apple.com";
    private static final long APPLE_CLIENT_SECRET_EXPIRES_SECONDS = 60L * 60L * 24L * 180L;

    private final WebClient webClient;
    private final SocialAuthProperties socialAuthProperties;

    /**
     * Apple JWKS와 token endpoint 호출에 사용할 WebClient를 구성한다.
     */
    public AppleSocialAuthProvider(WebClient.Builder webClientBuilder, SocialAuthProperties socialAuthProperties) {
        this.webClient = webClientBuilder.build();
        this.socialAuthProperties = socialAuthProperties;
    }

    /**
     * Apple 제공자 전략임을 반환한다.
     */
    @Override
    public SocialProvider supports() {
        return SocialProvider.APPLE;
    }

    /**
     * identity token을 검증하고 authorizationCode를 Apple token endpoint로 검증한다.
     */
    @Override
    public SocialUserInfo authenticate(SocialAuthCommand command) {
        if (!StringUtils.hasText(command.idToken())) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Apple idToken은 필수입니다.");
        }
        if (!StringUtils.hasText(command.authorizationCode())) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Apple authorizationCode는 필수입니다.");
        }
        SocialAuthProperties.Apple apple = getConfiguredAppleProperties();

        JWTClaimsSet identityClaims = verifyIdentityToken(command.idToken(), apple);
        verifyAuthorizationCode(command.authorizationCode(), identityClaims.getSubject(), apple);

        String email = getOptionalStringClaim(identityClaims, "email");
        return new SocialUserInfo(SocialProvider.APPLE, identityClaims.getSubject(), email);
    }

    /**
     * Apple 설정값이 모두 존재하는지 확인하고 설정 객체를 반환한다.
     */
    private SocialAuthProperties.Apple getConfiguredAppleProperties() {
        SocialAuthProperties.Apple apple = socialAuthProperties.apple();
        if (apple == null
                || !StringUtils.hasText(apple.clientId())
                || !StringUtils.hasText(apple.teamId())
                || !StringUtils.hasText(apple.keyId())
                || !StringUtils.hasText(apple.privateKey())
                || !StringUtils.hasText(apple.jwksUri())
                || !StringUtils.hasText(apple.tokenUri())) {
            throw new ApiException(ErrorCode.INTERNAL_SERVER_ERROR, "Apple 로그인 설정이 완료되지 않았습니다.");
        }
        return apple;
    }

    /**
     * Apple identity token의 RS256 서명과 핵심 claim을 검증한다.
     */
    private JWTClaimsSet verifyIdentityToken(String token, SocialAuthProperties.Apple apple) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            validateHeader(signedJWT.getHeader());
            RSAKey rsaKey = findApplePublicKey(signedJWT.getHeader().getKeyID(), apple);
            if (!signedJWT.verify(new RSASSAVerifier(rsaKey.toRSAPublicKey()))) {
                throw new ApiException(ErrorCode.UNAUTHORIZED, "Apple identity token 서명이 올바르지 않습니다.");
            }

            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            validateClaims(claims, apple);
            return claims;
        } catch (ParseException | JOSEException exception) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Apple 인증 정보가 올바르지 않습니다.");
        }
    }

    /**
     * Apple identity token 헤더가 RS256 JWT 형식인지 확인한다.
     */
    private void validateHeader(JWSHeader header) {
        if (!JWSAlgorithm.RS256.equals(header.getAlgorithm())) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Apple identity token 알고리즘이 올바르지 않습니다.");
        }
        if (header.getType() != null && !JOSEObjectType.JWT.equals(header.getType())) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Apple identity token 타입이 올바르지 않습니다.");
        }
        if (!StringUtils.hasText(header.getKeyID())) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Apple identity token key id가 없습니다.");
        }
    }

    /**
     * Apple JWKS에서 identity token 헤더의 kid와 일치하는 공개키를 조회한다.
     */
    private RSAKey findApplePublicKey(String keyId, SocialAuthProperties.Apple apple) {
        try {
            String jwks = webClient.get()
                    .uri(apple.jwksUri())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            if (!StringUtils.hasText(jwks)) {
                throw new ApiException(ErrorCode.UNAUTHORIZED, "Apple 공개키를 조회할 수 없습니다.");
            }

            JWK jwk = JWKSet.parse(jwks).getKeyByKeyId(keyId);
            if (!(jwk instanceof RSAKey rsaKey)) {
                throw new ApiException(ErrorCode.UNAUTHORIZED, "Apple identity token 공개키를 찾을 수 없습니다.");
            }
            return rsaKey;
        } catch (ParseException exception) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Apple 공개키 응답이 올바르지 않습니다.");
        } catch (RuntimeException exception) {
            if (exception instanceof ApiException apiException) {
                throw apiException;
            }
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Apple 공개키 조회에 실패했습니다.");
        }
    }

    /**
     * Apple identity token의 issuer, audience, 만료 시간, subject를 검증한다.
     */
    private void validateClaims(JWTClaimsSet claims, SocialAuthProperties.Apple apple) {
        if (!APPLE_ISSUER.equals(claims.getIssuer())) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Apple issuer가 올바르지 않습니다.");
        }
        if (claims.getAudience() == null || !claims.getAudience().contains(apple.clientId())) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Apple audience가 올바르지 않습니다.");
        }
        if (!StringUtils.hasText(claims.getSubject())) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Apple subject가 없습니다.");
        }
        Date expirationTime = claims.getExpirationTime();
        if (expirationTime == null || expirationTime.toInstant().isBefore(Instant.now())) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Apple identity token이 만료되었습니다.");
        }
    }

    /**
     * authorizationCode를 Apple token endpoint로 교환하고 반환 id_token의 subject 일치를 확인한다.
     */
    private void verifyAuthorizationCode(
            String authorizationCode,
            String expectedSubject,
            SocialAuthProperties.Apple apple
    ) {
        Map<?, ?> tokenResponse = requestToken(authorizationCode, apple);
        Object exchangedIdToken = tokenResponse.get("id_token");
        if (exchangedIdToken == null || !StringUtils.hasText(String.valueOf(exchangedIdToken))) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Apple authorizationCode 검증 응답이 올바르지 않습니다.");
        }

        JWTClaimsSet exchangedClaims = verifyIdentityToken(String.valueOf(exchangedIdToken), apple);
        if (!expectedSubject.equals(exchangedClaims.getSubject())) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Apple authorizationCode의 subject가 일치하지 않습니다.");
        }
    }

    /**
     * Apple token endpoint에 authorizationCode 교환 요청을 보낸다.
     */
    private Map<?, ?> requestToken(String authorizationCode, SocialAuthProperties.Apple apple) {
        String clientSecret = createClientSecret(apple);
        try {
            Map<?, ?> response = webClient.post()
                    .uri(apple.tokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData("grant_type", "authorization_code")
                            .with("code", authorizationCode)
                            .with("client_id", apple.clientId())
                            .with("client_secret", clientSecret))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            if (response == null) {
                throw new ApiException(ErrorCode.UNAUTHORIZED, "Apple authorizationCode 검증 응답이 비어 있습니다.");
            }
            return response;
        } catch (RuntimeException exception) {
            if (exception instanceof ApiException apiException) {
                throw apiException;
            }
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Apple authorizationCode 검증에 실패했습니다.");
        }
    }

    /**
     * Apple token endpoint 호출에 사용할 ES256 client_secret JWT를 생성한다.
     */
    private String createClientSecret(SocialAuthProperties.Apple apple) {
        try {
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(apple.teamId())
                    .subject(apple.clientId())
                    .audience(APPLE_CLIENT_SECRET_AUDIENCE)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusSeconds(APPLE_CLIENT_SECRET_EXPIRES_SECONDS)))
                    .build();
            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.ES256)
                            .type(JOSEObjectType.JWT)
                            .keyID(apple.keyId())
                            .build(),
                    claims
            );
            signedJWT.sign(new ECDSASigner(readApplePrivateKey(apple.privateKey())));
            return signedJWT.serialize();
        } catch (Exception exception) {
            throw new ApiException(ErrorCode.INTERNAL_SERVER_ERROR, "Apple client_secret 생성에 실패했습니다.");
        }
    }

    /**
     * Apple .p8 private key PEM 문자열을 ECPrivateKey로 변환한다.
     */
    private ECPrivateKey readApplePrivateKey(String privateKey) throws Exception {
        String normalized = privateKey
                .replace("\\n", "\n")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(normalized);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        return (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(keySpec);
    }

    /**
     * claim이 존재할 때 문자열로 반환하고 없으면 null을 반환한다.
     */
    private String getOptionalStringClaim(JWTClaimsSet claims, String claimName) {
        Object value = claims.getClaim(claimName);
        return value == null ? null : String.valueOf(value);
    }
}
