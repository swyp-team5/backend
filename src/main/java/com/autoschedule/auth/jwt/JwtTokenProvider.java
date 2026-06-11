package com.autoschedule.auth.jwt;

import com.autoschedule.auth.config.JwtProperties;
import com.autoschedule.auth.domain.TokenType;
import com.autoschedule.global.exception.ApiException;
import com.autoschedule.global.exception.ErrorCode;
import com.autoschedule.member.domain.Member;
import com.autoschedule.member.domain.MemberRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * 우리 서버 인증/인가에 사용하는 JWT 발급과 검증을 담당한다.
 */
@Component
public class JwtTokenProvider {

    private static final String TOKEN_TYPE_CLAIM = "typ";
    private static final String ROLE_CLAIM = "role";

    private final JwtProperties jwtProperties;
    private final SecretKey secretKey;

    /**
     * JWT 서명에 사용할 HMAC 키를 설정값에서 생성한다.
     */
    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 회원에게 access token과 refresh token을 함께 발급한다.
     */
    public IssuedTokens issue(Member member) {
        return new IssuedTokens(
                createToken(member, TokenType.ACCESS, jwtProperties.accessTokenValiditySeconds()),
                createToken(member, TokenType.REFRESH, jwtProperties.refreshTokenValiditySeconds()),
                jwtProperties.accessTokenValiditySeconds(),
                jwtProperties.refreshTokenValiditySeconds()
        );
    }

    /**
     * 지정한 토큰 유형과 만료 시간으로 JWT를 생성한다.
     */
    public String createToken(Member member, TokenType tokenType, long expiresInSeconds) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(expiresInSeconds);
        return Jwts.builder()
                .subject(String.valueOf(member.getId()))
                .claim(TOKEN_TYPE_CLAIM, tokenType.name())
                .claim(ROLE_CLAIM, member.getRole().name())
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey)
                .compact();
    }

    /**
     * access token을 검증하고 인증 주체 정보를 반환한다.
     */
    public JwtAuthenticationPrincipal validateAccessToken(String token) {
        Claims claims = parseClaims(token);
        validateTokenType(claims, TokenType.ACCESS);
        return new JwtAuthenticationPrincipal(
                Long.valueOf(claims.getSubject()),
                MemberRole.valueOf(claims.get(ROLE_CLAIM, String.class))
        );
    }

    /**
     * refresh token을 검증하고 회원 ID를 반환한다.
     */
    public Long validateRefreshToken(String token) {
        Claims claims = parseClaims(token);
        validateTokenType(claims, TokenType.REFRESH);
        return Long.valueOf(claims.getSubject());
    }

    /**
     * JWT 서명, 만료 시간, 기본 구조를 검증하고 claims를 파싱한다.
     */
    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "유효하지 않은 토큰입니다.");
        }
    }

    /**
     * 토큰에 포함된 용도 claim이 기대한 토큰 유형과 일치하는지 확인한다.
     */
    private void validateTokenType(Claims claims, TokenType expectedTokenType) {
        String tokenType = claims.get(TOKEN_TYPE_CLAIM, String.class);
        if (!expectedTokenType.name().equals(tokenType)) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "토큰 유형이 올바르지 않습니다.");
        }
    }
}
