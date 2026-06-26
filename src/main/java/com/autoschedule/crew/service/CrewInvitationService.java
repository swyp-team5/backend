package com.autoschedule.crew.service;

import com.autoschedule.crew.domain.Crew;
import com.autoschedule.crew.domain.CrewInvitation;
import com.autoschedule.crew.domain.CrewInvitationStatus;
import com.autoschedule.crew.dto.CrewInvitationAcceptResponse;
import com.autoschedule.crew.dto.CrewInvitationCreateResponse;
import com.autoschedule.crew.dto.CrewInvitationHistoryResponse;
import com.autoschedule.crew.redis.CrewInvitationRedisStore;
import com.autoschedule.crew.repository.CrewInvitationRepository;
import com.autoschedule.crew.repository.CrewRepository;
import com.autoschedule.global.exception.ApiException;
import com.autoschedule.global.exception.ErrorCode;
import com.autoschedule.member.domain.Member;
import com.autoschedule.member.repository.MemberRepository;
import com.autoschedule.workplace.domain.WorkPlace;
import com.autoschedule.workplace.repository.WorkPlaceRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 크루 초대 코드 생성, 검증, 수락 유스케이스를 처리한다.
 */
@Service
@RequiredArgsConstructor
public class CrewInvitationService {

    private static final Duration INVITATION_TTL = Duration.ofHours(1);
    private static final int INVITE_CODE_BOUND = 1_000_000;
    private static final int MAX_CODE_GENERATION_ATTEMPTS = 10;
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final String INVITE_CODE_PATTERN = "\\d{6}";

    private final SecureRandom secureRandom = new SecureRandom();
    private final MemberRepository memberRepository;
    private final WorkPlaceRepository workPlaceRepository;
    private final CrewRepository crewRepository;
    private final CrewInvitationRepository crewInvitationRepository;
    private final CrewInvitationRedisStore crewInvitationRedisStore;

    /**
     * 사장님이 소유한 사업장에 대해 1시간 유효한 6자리 초대 코드를 생성한다.
     */
    @Transactional
    public CrewInvitationCreateResponse createInvitation(Long ownerMemberId, Long workPlaceId) {
        Member owner = findActiveMember(ownerMemberId);
        WorkPlace workPlace = findOwnedActiveWorkPlace(workPlaceId, owner);

        String inviteCode = generateUniqueInviteCode();
        LocalDateTime expiresAt = LocalDateTime.now().plus(INVITATION_TTL);
        CrewInvitation invitation = crewInvitationRepository.save(
                CrewInvitation.create(workPlace, owner.getId(), inviteCode, expiresAt)
        );

        registerAfterCommit(() -> crewInvitationRedisStore.saveInvitation(
                invitation.getInviteCode(),
                invitation.getId(),
                INVITATION_TTL
        ));

        return CrewInvitationCreateResponse.of(
                invitation.getId(),
                workPlace.getId(),
                invitation.getInviteCode(),
                invitation.getExpiresAt()
        );
    }

    /**
     * 근무자가 초대 코드를 수락하면 사업장 크루로 즉시 승인 등록한다.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public CrewInvitationAcceptResponse acceptInvitation(Long workerMemberId, String inviteCode) {
        validateInviteCodeFormat(inviteCode);
        Member worker = findActiveMember(workerMemberId);
        CrewInvitation invitation = crewInvitationRepository.findByInviteCodeForUpdate(inviteCode)
                .orElseThrow(() -> {
                    crewInvitationRedisStore.incrementFailedAttempt(inviteCode, INVITATION_TTL);
                    return new ApiException(ErrorCode.INVALID_REQUEST, "초대 코드가 올바르지 않습니다.");
                });

        validateAttemptLimit(invitation);
        validateInvitationUsable(invitation);
        validateNotAlreadyCrew(worker, invitation.getWorkPlace());

        Crew crew = createOrReactivateWorkerCrew(worker, invitation.getWorkPlace());
        invitation.markUsed(worker, LocalDateTime.now());
        registerAfterCommit(() -> crewInvitationRedisStore.deleteAll(inviteCode));
        return CrewInvitationAcceptResponse.from(crew);
    }

    /**
     * 사장님이 본인 사업장에서 생성한 초대 코드 발급/사용 이력을 최신순으로 조회한다.
     */
    /**
     * 과거 비활성 크루 이력이 있으면 재활성화하고, 없으면 신규 크루를 생성한다.
     */
    private Crew createOrReactivateWorkerCrew(Member worker, WorkPlace workPlace) {
        return crewRepository.findByMember_IdAndWorkPlace_Id(worker.getId(), workPlace.getId())
                .map(crew -> {
                    crew.reactivateWorker();
                    return crew;
                })
                .orElseGet(() -> crewRepository.save(Crew.createWorker(worker, workPlace)));
    }

    @Transactional(readOnly = true)
    public CrewInvitationHistoryResponse getInvitationHistory(
            Long ownerMemberId,
            Long workPlaceId,
            int page,
            int size
    ) {
        Member owner = findActiveMember(ownerMemberId);
        WorkPlace workPlace = findOwnedActiveWorkPlace(workPlaceId, owner);
        PageRequest pageRequest = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))
        );
        Page<CrewInvitation> invitations = crewInvitationRepository.findByWorkPlace_IdAndDeletedAtIsNull(
                workPlace.getId(),
                pageRequest
        );
        Map<Long, String> usedMemberNames = findUsedMemberNames(invitations);
        return CrewInvitationHistoryResponse.from(invitations, usedMemberNames);
    }

    /**
     * 현재 실패 횟수가 제한을 넘었으면 초대 코드를 잠근다.
     */
    private void validateAttemptLimit(CrewInvitation invitation) {
        int failedAttemptCount = crewInvitationRedisStore.getFailedAttemptCount(invitation.getInviteCode());
        if (failedAttemptCount >= MAX_FAILED_ATTEMPTS) {
            invitation.markLocked(failedAttemptCount);
            crewInvitationRedisStore.deleteAll(invitation.getInviteCode());
            throw new ApiException(ErrorCode.INVALID_REQUEST, "초대 코드 입력 가능 횟수를 초과했습니다.");
        }
    }

    /**
     * 초대 코드가 활성 상태이고 만료되지 않았는지 확인한다.
     */
    private void validateInvitationUsable(CrewInvitation invitation) {
        LocalDateTime now = LocalDateTime.now();
        if (invitation.isExpired(now)) {
            invitation.markExpired();
            recordFailedAttempt(invitation);
            throw new ApiException(ErrorCode.INVALID_REQUEST, "초대 코드가 만료되었습니다.");
        }

        if (invitation.getStatus() != CrewInvitationStatus.ACTIVE) {
            recordFailedAttempt(invitation);
            throw new ApiException(ErrorCode.INVALID_REQUEST, "사용할 수 없는 초대 코드입니다.");
        }
    }

    /**
     * 같은 근무자가 같은 사업장에 중복 가입하지 못하도록 검증한다.
     */
    private void validateNotAlreadyCrew(Member worker, WorkPlace workPlace) {
        if (crewRepository.existsActiveByMemberIdAndWorkPlaceId(worker.getId(), workPlace.getId())) {
            throw new ApiException(ErrorCode.CONFLICT, "이미 해당 사업장 크루로 등록되어 있습니다.");
        }
    }

    /**
     * 실패 횟수를 Redis에 기록하고 제한 도달 시 RDB 상태를 잠금으로 변경한다.
     */
    private void recordFailedAttempt(CrewInvitation invitation) {
        int failedAttemptCount = crewInvitationRedisStore.incrementFailedAttempt(
                invitation.getInviteCode(),
                INVITATION_TTL
        );
        if (failedAttemptCount >= MAX_FAILED_ATTEMPTS) {
            invitation.markLocked(failedAttemptCount);
            return;
        }
        invitation.updateFailedAttemptCount(failedAttemptCount);
    }

    /**
     * 초대 코드가 6자리 숫자 형식인지 확인한다.
     */
    private void validateInviteCodeFormat(String inviteCode) {
        if (inviteCode == null || !inviteCode.matches(INVITE_CODE_PATTERN)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "초대 코드는 6자리 숫자여야 합니다.");
        }
    }

    /**
     * 활성 회원만 초대 생성과 수락을 수행할 수 있도록 조회한다.
     */
    private Member findActiveMember(Long memberId) {
        return memberRepository.findActiveById(memberId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "인증 정보가 올바르지 않습니다."));
    }

    /**
     * 사장님이 소유한 활성 사업장을 조회하고 없으면 404 오류를 반환한다.
     */
    private WorkPlace findOwnedActiveWorkPlace(Long workPlaceId, Member owner) {
        return workPlaceRepository.findOwnedActiveById(workPlaceId, owner.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "초대할 수 있는 사업장을 찾을 수 없습니다."));
    }

    /**
     * 초대를 수락한 회원 ID 스냅샷을 기준으로 응답에 표시할 회원명을 조회한다.
     */
    private Map<Long, String> findUsedMemberNames(Page<CrewInvitation> invitations) {
        Set<Long> usedMemberIds = invitations.getContent()
                .stream()
                .map(CrewInvitation::getUsedByMemberId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (usedMemberIds.isEmpty()) {
            return Map.of();
        }

        return memberRepository.findActiveByIdIn(usedMemberIds)
                .stream()
                .collect(Collectors.toMap(Member::getId, Member::getName, (first, second) -> first));
    }

    /**
     * DB에 없는 6자리 숫자 초대 코드를 생성한다.
     */
    private String generateUniqueInviteCode() {
        for (int attempt = 0; attempt < MAX_CODE_GENERATION_ATTEMPTS; attempt++) {
            String inviteCode = "%06d".formatted(secureRandom.nextInt(INVITE_CODE_BOUND));
            if (!crewInvitationRepository.existsByInviteCode(inviteCode)) {
                return inviteCode;
            }
        }
        throw new ApiException(ErrorCode.INTERNAL_SERVER_ERROR, "초대 코드 생성에 실패했습니다.");
    }

    /**
     * 트랜잭션 커밋 성공 이후에만 Redis 변경을 반영한다.
     */
    private void registerAfterCommit(Runnable runnable) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            runnable.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            /**
             * DB 커밋이 완료된 뒤 Redis 상태를 변경한다.
             */
            @Override
            public void afterCommit() {
                runnable.run();
            }
        });
    }
}
