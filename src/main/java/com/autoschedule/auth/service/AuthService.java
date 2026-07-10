package com.autoschedule.auth.service;

import com.autoschedule.auth.dto.AuthResponse;
import com.autoschedule.auth.dto.DeviceRequest;
import com.autoschedule.auth.dto.OwnerSignupRequest;
import com.autoschedule.auth.dto.RefreshTokenRequest;
import com.autoschedule.auth.dto.SocialLoginRequest;
import com.autoschedule.auth.dto.TermsAgreementRequest;
import com.autoschedule.auth.dto.WorkerSignupRequest;
import com.autoschedule.auth.jwt.IssuedTokens;
import com.autoschedule.auth.jwt.JwtTokenProvider;
import com.autoschedule.auth.jwt.TokenHashService;
import com.autoschedule.auth.refresh.RefreshTokenSession;
import com.autoschedule.auth.refresh.RefreshTokenStore;
import com.autoschedule.auth.social.AppleSocialAuthProvider;
import com.autoschedule.auth.social.SocialAuthCommand;
import com.autoschedule.auth.social.SocialAuthProviderRegistry;
import com.autoschedule.auth.social.SocialUserInfo;
import com.autoschedule.global.exception.ApiException;
import com.autoschedule.global.exception.ErrorCode;
import com.autoschedule.member.domain.Member;
import com.autoschedule.member.domain.MemberRole;
import com.autoschedule.member.domain.MemberStatus;
import com.autoschedule.member.domain.SocialProvider;
import com.autoschedule.member.repository.MemberRepository;
import com.autoschedule.terms.domain.MemberTermsAgreement;
import com.autoschedule.terms.domain.Terms;
import com.autoschedule.terms.domain.TermsStatus;
import com.autoschedule.terms.domain.TermsType;
import com.autoschedule.terms.repository.MemberTermsAgreementRepository;
import com.autoschedule.terms.repository.TermsRepository;
import com.autoschedule.terms.service.SignupTermsPolicy;
import com.autoschedule.workplace.service.WorkPlaceCreateCommand;
import com.autoschedule.workplace.service.WorkPlaceService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 소셜 로그인, 회원가입, JWT 재발급 인증 유스케이스를 처리한다.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Duration WITHDRAWAL_GRACE_PERIOD = Duration.ofDays(30);

    private final SocialAuthProviderRegistry socialAuthProviderRegistry;
    private final MemberRepository memberRepository;
    private final TermsRepository termsRepository;
    private final MemberTermsAgreementRepository memberTermsAgreementRepository;
    private final WorkPlaceService workPlaceService;
    private final RefreshTokenStore refreshTokenStore;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenHashService tokenHashService;
    private final SignupTermsPolicy signupTermsPolicy;
    private final AppleSocialAuthProvider appleSocialAuthProvider;

    /**
     * 소셜 인증 정보를 검증하고 기존 회원이면 로그인 토큰을 발급한다.
     */
    @Transactional
    public AuthResponse socialLogin(SocialLoginRequest request) {
        SocialUserInfo socialUserInfo = authenticate(toCommand(
                request.provider(),
                request.idToken(),
                request.accessToken(),
                request.authorizationCode()
        ));

        return memberRepository.findBySocialProviderAndSocialSubject(
                        socialUserInfo.provider(),
                        socialUserInfo.subject()
                )
                .map(member -> loginExistingMember(member, request.device()))
                .orElseGet(AuthResponse::signupRequired);
    }

    /**
     * 사장 필수 정보를 저장하고 최초 사업장과 사장 소속을 생성한 뒤 로그인 토큰을 발급한다.
     */
    @Transactional
    public AuthResponse signupOwner(OwnerSignupRequest request) {
        SocialUserInfo socialUserInfo = authenticateForSignup(toCommand(
                request.provider(),
                request.idToken(),
                request.accessToken(),
                request.authorizationCode()
        ));
        ensureNotRegistered(socialUserInfo);
        validateRequiredTerms(request.termsAgreements(), signupTermsPolicy.resolve(MemberRole.OWNER));

        Member member = memberRepository.save(Member.create(
                socialUserInfo.provider(),
                socialUserInfo.subject(),
                socialUserInfo.email(),
                request.name(),
                request.phoneNumber(),
                MemberRole.OWNER
        ));
        workPlaceService.createInitialWorkPlaceForSignup(member, new WorkPlaceCreateCommand(
                request.workPlace().size(),
                request.workPlace().name(),
                request.workPlace().roadAddress(),
                request.workPlace().detailAddress(),
                request.workPlace().phoneNumber()
        ));
        saveTermsAgreements(member.getId(), request.termsAgreements());
        return issueLoginResponse(member, request.device());
    }

    /**
     * 근무자 필수 정보를 저장하고 로그인 토큰을 발급한다.
     */
    @Transactional
    public AuthResponse signupWorker(WorkerSignupRequest request) {
        SocialUserInfo socialUserInfo = authenticateForSignup(toCommand(
                request.provider(),
                request.idToken(),
                request.accessToken(),
                request.authorizationCode()
        ));
        ensureNotRegistered(socialUserInfo);
        validateRequiredTerms(request.termsAgreements(), signupTermsPolicy.resolve(MemberRole.WORKER));

        Member member = memberRepository.save(Member.create(
                socialUserInfo.provider(),
                socialUserInfo.subject(),
                socialUserInfo.email(),
                request.name(),
                request.phoneNumber(),
                MemberRole.WORKER
        ));
        saveTermsAgreements(member.getId(), request.termsAgreements());
        return issueLoginResponse(member, request.device());
    }

    /**
     * Redis에 저장된 기기별 refresh token hash와 요청 token hash를 비교한 뒤 새 토큰으로 교체한다.
     */
    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        Long memberId = jwtTokenProvider.validateRefreshToken(request.refreshToken());
        String requestTokenHash = tokenHashService.hash(request.refreshToken());
        String savedTokenHash = refreshTokenStore.findTokenHash(memberId, request.deviceId())
                .orElseThrow(this::invalidRefreshToken);

        if (!savedTokenHash.equals(requestTokenHash)) {
            throw invalidRefreshToken();
        }

        Member member = memberRepository.findActiveById(memberId)
                .orElseThrow(this::invalidRefreshToken);
        IssuedTokens tokens = jwtTokenProvider.issue(member);
        saveRefreshToken(member, request.deviceId(), tokens);
        return AuthResponse.loginSuccess(tokens, member);
    }

    /**
     * 저장된 refresh token hash와 요청 token hash가 일치하면 해당 기기의 refresh token 세션을 삭제한다.
     */
    @Transactional
    public void logout(RefreshTokenRequest request) {
        Long memberId = jwtTokenProvider.validateRefreshToken(request.refreshToken());
        String requestTokenHash = tokenHashService.hash(request.refreshToken());
        String savedTokenHash = refreshTokenStore.findTokenHash(memberId, request.deviceId())
                .orElseThrow(this::invalidRefreshToken);

        if (!savedTokenHash.equals(requestTokenHash)) {
            throw invalidRefreshToken();
        }

        memberRepository.findActiveById(memberId)
                .orElseThrow(this::invalidRefreshToken);
        refreshTokenStore.delete(memberId, request.deviceId());
    }

    /**
     * 기존 회원의 로그인 가능 상태를 확인한 뒤 JWT와 refresh token을 발급한다.
     */
    private AuthResponse loginExistingMember(Member member, DeviceRequest device) {
        validateLoginAllowed(member);
        return issueLoginResponse(member, device);
    }

    /**
     * 정상 회원과 30일 유예 기간 안의 탈퇴 신청 회원만 로그인을 허용한다.
     */
    private void validateLoginAllowed(Member member) {
        if (member.getStatus() == MemberStatus.ACTIVE) {
            return;
        }

        if (member.isWithinWithdrawalGracePeriod(LocalDateTime.now(), WITHDRAWAL_GRACE_PERIOD)) {
            return;
        }

        if (member.getStatus() == MemberStatus.WITHDRAWAL_PENDING) {
            throw new ApiException(ErrorCode.CONFLICT, "탈퇴 취소 가능 기간이 지나 로그인할 수 없습니다.");
        }

        throw new ApiException(ErrorCode.CONFLICT, "탈퇴 완료된 회원은 로그인할 수 없습니다.");
    }

    /**
     * provider별 필수 토큰 규칙을 검증한 뒤 해당 소셜 전략으로 인증한다.
     */
    private SocialUserInfo authenticate(SocialAuthCommand command) {
        validateProviderTokenShape(command);
        return socialAuthProviderRegistry.get(command.provider()).authenticate(command);
    }

    /**
     * 회원가입 완료 단계의 소셜 인증 정보를 검증한다.
     *
     * Apple은 social-login 단계에서 authorizationCode를 이미 검증하므로,
     * signup 단계에서는 authorizationCode를 재검증하지 않고 identity token만 검증한다.
     */
    private SocialUserInfo authenticateForSignup(SocialAuthCommand command) {
        validateProviderTokenShapeForSignup(command);

        if (command.provider() == SocialProvider.APPLE) {
            return appleSocialAuthProvider.authenticateForSignup(command);
        }

        return socialAuthProviderRegistry.get(command.provider()).authenticate(command);
    }

    /**
     * 회원가입 완료 단계에서 제공자별 필수 토큰이 존재하는지 검증한다.
     */
    private void validateProviderTokenShapeForSignup(SocialAuthCommand command) {
        switch (command.provider()) {
            case GOOGLE -> requireText(command.idToken(), "Google idToken은 필수입니다.");
            case KAKAO -> requireText(command.accessToken(), "Kakao accessToken은 필수입니다.");
            case APPLE -> requireText(command.idToken(), "Apple idToken은 필수입니다.");
        }
    }

    /**
     * 요청 필드를 소셜 인증 전략에서 사용하는 command 객체로 변환한다.
     */
    private SocialAuthCommand toCommand(
            SocialProvider provider,
            String idToken,
            String accessToken,
            String authorizationCode
    ) {
        return new SocialAuthCommand(provider, idToken, accessToken, authorizationCode);
    }

    /**
     * 제공자별로 명세에서 요구한 필수 토큰이 존재하는지 검증한다.
     */
    private void validateProviderTokenShape(SocialAuthCommand command) {
        switch (command.provider()) {
            case GOOGLE -> requireText(command.idToken(), "Google idToken은 필수입니다.");
            case KAKAO -> requireText(command.accessToken(), "Kakao accessToken은 필수입니다.");
            case APPLE -> {
                requireText(command.idToken(), "Apple idToken은 필수입니다.");
                requireText(command.authorizationCode(), "Apple authorizationCode는 필수입니다.");
            }
        }
    }

    /**
     * 필수 문자열 값이 비어 있으면 잘못된 요청 예외를 발생시킨다.
     */
    private void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, message);
        }
    }

    /**
     * 동일 소셜 계정으로 이미 가입된 회원이 있으면 중복 가입을 막는다.
     */
    private void ensureNotRegistered(SocialUserInfo socialUserInfo) {
        if (memberRepository.existsBySocialProviderAndSocialSubject(
                socialUserInfo.provider(),
                socialUserInfo.subject()
        )) {
            throw new ApiException(ErrorCode.CONFLICT, "이미 가입된 소셜 계정입니다.");
        }
    }

    /**
     * 활성 필수 약관을 모두 true로 동의했는지 검증한다.
     */
    private void validateRequiredTerms(List<TermsAgreementRequest> agreements, Set<TermsType> termsTypes) {
        Map<Long, Boolean> agreedByTermsId = agreements.stream()
                .collect(Collectors.toMap(
                        TermsAgreementRequest::termsId,
                        TermsAgreementRequest::agreed,
                        (left, right) -> right
                ));
        List<Terms> activeTerms = termsRepository.findByTermsTypeInAndStatus(termsTypes, TermsStatus.ACTIVE);
        for (Terms terms : activeTerms) {
            if (terms.isRequired() && !Boolean.TRUE.equals(agreedByTermsId.get(terms.getId()))) {
                throw new ApiException(ErrorCode.INVALID_REQUEST, "필수 약관에 동의해야 합니다.");
            }
        }
    }

    /**
     * 요청으로 전달된 약관 ID를 검증하고 회원 약관 동의 이력을 저장한다.
     */
    private void saveTermsAgreements(Long memberId, List<TermsAgreementRequest> agreements) {
        Map<Long, Terms> termsById = termsRepository.findAllById(
                        agreements.stream()
                                .map(TermsAgreementRequest::termsId)
                                .toList()
                )
                .stream()
                .collect(Collectors.toMap(Terms::getId, Function.identity()));

        List<MemberTermsAgreement> agreementEntities = agreements.stream()
                .map(agreement -> {
                    Terms terms = termsById.get(agreement.termsId());
                    if (terms == null) {
                        throw new ApiException(ErrorCode.INVALID_REQUEST, "존재하지 않는 약관입니다.");
                    }
                    return MemberTermsAgreement.create(memberId, terms, agreement.agreed());
                })
                .toList();
        memberTermsAgreementRepository.saveAll(agreementEntities);
    }

    /**
     * 회원에게 JWT를 발급하고 기기별 refresh token hash를 Redis에 저장한 뒤 로그인 성공 응답을 만든다.
     */
    private AuthResponse issueLoginResponse(Member member, DeviceRequest device) {
        IssuedTokens tokens = jwtTokenProvider.issue(member);
        saveRefreshToken(member, device.deviceId(), tokens);
        return AuthResponse.loginSuccess(tokens, member);
    }

    /**
     * 같은 회원과 기기의 기존 refresh token hash를 새 hash로 덮어쓰고 TTL을 갱신한다.
     */
    private void saveRefreshToken(Member member, String deviceId, IssuedTokens tokens) {
        RefreshTokenSession session = new RefreshTokenSession(
                member.getId(),
                deviceId,
                tokenHashService.hash(tokens.refreshToken()),
                Duration.ofSeconds(tokens.refreshTokenExpiresIn())
        );

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

                /**
                 * DB 트랜잭션이 성공적으로 커밋된 뒤 Redis refresh token 세션을 저장한다.
                 */
                @Override
                public void afterCommit() {
                    refreshTokenStore.save(session);
                }
            });
            return;
        }

        refreshTokenStore.save(session);
    }

    /**
     * refresh token 검증 실패 시 동일한 401 예외를 반환한다.
     */
    private ApiException invalidRefreshToken() {
        return new ApiException(ErrorCode.UNAUTHORIZED, "refresh token이 유효하지 않습니다.");
    }
}
