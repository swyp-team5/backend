package com.autoschedule.notice.service;

import com.autoschedule.auth.jwt.JwtAuthenticationPrincipal;
import com.autoschedule.crew.domain.CrewJoinStatus;
import com.autoschedule.crew.domain.CrewRole;
import com.autoschedule.crew.domain.CrewStatus;
import com.autoschedule.crew.repository.CrewRepository;
import com.autoschedule.global.exception.ApiException;
import com.autoschedule.global.exception.ErrorCode;
import com.autoschedule.member.domain.Member;
import com.autoschedule.member.domain.MemberRole;
import com.autoschedule.member.domain.MemberStatus;
import com.autoschedule.member.repository.MemberRepository;
import com.autoschedule.notice.domain.Notice;
import com.autoschedule.notice.domain.NoticeComment;
import com.autoschedule.notice.domain.NoticeCommentStatus;
import com.autoschedule.notice.domain.NoticeStatus;
import com.autoschedule.notice.dto.HomeNoticeSummaryResponse;
import com.autoschedule.notice.dto.HomeRepresentativeNoticeResponse;
import com.autoschedule.notice.dto.NoticeCommentCursorResponse;
import com.autoschedule.notice.dto.NoticeCommentRequest;
import com.autoschedule.notice.dto.NoticeCommentResponse;
import com.autoschedule.notice.dto.NoticeCreateRequest;
import com.autoschedule.notice.dto.NoticePageResponse;
import com.autoschedule.notice.dto.NoticeResponse;
import com.autoschedule.notice.dto.NoticeUpdateRequest;
import com.autoschedule.notice.dto.RepresentativeNoticeResponse;
import com.autoschedule.notice.repository.NoticeCommentRepository;
import com.autoschedule.notice.repository.NoticeRepository;
import com.autoschedule.notification.domain.NotificationType;
import com.autoschedule.notification.domain.PushPolicy;
import com.autoschedule.notification.dto.NotificationSendCommand;
import com.autoschedule.notification.service.NotificationCommandService;
import com.autoschedule.workplace.domain.WorkPlace;
import com.autoschedule.workplace.domain.WorkPlaceStatus;
import com.autoschedule.workplace.repository.WorkPlaceRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사업장 공지와 공지 댓글의 비즈니스 규칙을 처리한다.
 */
@Service
@RequiredArgsConstructor
public class NoticeService {

    private static final int MAX_COMMENT_SCROLL_SIZE = 100;

    private final MemberRepository memberRepository;
    private final WorkPlaceRepository workPlaceRepository;
    private final CrewRepository crewRepository;
    private final NoticeRepository noticeRepository;
    private final NoticeCommentRepository noticeCommentRepository;
    private final NotificationCommandService notificationCommandService;

    /**
     * 사장님이 소유한 사업장에 새 공지를 작성한다.
     */
    @Transactional
    public NoticeResponse createNotice(Long ownerMemberId, Long workPlaceId, NoticeCreateRequest request) {
        Member owner = findActiveMember(ownerMemberId);
        WorkPlace workPlace = findOwnedActiveWorkPlace(workPlaceId, owner.getId());

        Notice notice = noticeRepository.save(Notice.create(
                workPlace,
                owner.getId(),
                request.title(),
                request.content(),
                request.representative()
        ));
        applyRepresentativePolicy(notice);
        sendNoticeCreatedNotifications(workPlace, notice);

        return NoticeResponse.from(notice, owner.getName());
    }

    /**
     * 공지가 작성된 사업장의 승인된 근무자들에게 앱 알림과 FCM 푸시를 요청한다.
     */
    private void sendNoticeCreatedNotifications(WorkPlace workPlace, Notice notice) {
        List<Long> workerMemberIds = crewRepository.findMemberIdsByWorkPlaceAndRole(
                workPlace.getId(),
                CrewJoinStatus.APPROVED,
                CrewRole.WORKER,
                CrewStatus.ACTIVE
        );
        for (Long workerMemberId : workerMemberIds) {
            notificationCommandService.sendToMember(
                    workerMemberId,
                    new NotificationSendCommand(
                            NotificationType.NOTICE,
                            PushPolicy.PUSH,
                            "새 공지가 등록됐어요",
                            notice.getTitle(),
                            Map.of(
                                    "type", NotificationType.NOTICE.name(),
                                    "workPlaceId", String.valueOf(workPlace.getId()),
                                    "noticeId", String.valueOf(notice.getId())
                            )
                    )
            );
        }
    }

    /**
     * 사업장에 접근 가능한 회원이 공지 목록을 최신순으로 조회한다.
     */
    @Transactional(readOnly = true)
    public NoticePageResponse getNotices(
            JwtAuthenticationPrincipal principal,
            Long workPlaceId,
            int page,
            int size
    ) {
        validateCanReadWorkPlace(principal, workPlaceId);
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))
        );
        Page<Notice> notices = noticeRepository.findByWorkPlace_IdAndStatusAndDeletedAtIsNull(
                workPlaceId,
                NoticeStatus.ACTIVE,
                pageable
        );
        Map<Long, String> writerNames = resolveWriterNames(notices.getContent());
        return NoticePageResponse.from(notices, writerNames);
    }

    /**
     * 사업장의 대표 공지를 조회한다.
     */
    @Transactional(readOnly = true)
    public RepresentativeNoticeResponse getRepresentativeNotice(
            JwtAuthenticationPrincipal principal,
            Long workPlaceId
    ) {
        validateCanReadWorkPlace(principal, workPlaceId);
        NoticeResponse notice = noticeRepository
                .findFirstByWorkPlace_IdAndRepresentativeTrueAndStatusAndDeletedAtIsNull(
                        workPlaceId,
                        NoticeStatus.ACTIVE
                )
                .map(value -> NoticeResponse.from(value, resolveWriterName(value.getWriterMemberId())))
                .orElse(null);
        return new RepresentativeNoticeResponse(notice);
    }

    /**
     * 홈 화면에 표시할 대표 공지 요약 정보를 조회한다.
     */
    @Transactional(readOnly = true)
    public HomeRepresentativeNoticeResponse getHomeRepresentativeNotice(
            JwtAuthenticationPrincipal principal,
            Long workPlaceId
    ) {
        validateCanReadWorkPlace(principal, workPlaceId);
        HomeNoticeSummaryResponse notice = noticeRepository
                .findFirstByWorkPlace_IdAndRepresentativeTrueAndStatusAndDeletedAtIsNull(
                        workPlaceId,
                        NoticeStatus.ACTIVE
                )
                .map(value -> HomeNoticeSummaryResponse.from(value, resolveWriterName(value.getWriterMemberId())))
                .orElse(null);
        return new HomeRepresentativeNoticeResponse(notice);
    }

    /**
     * 홈 화면에 표시할 최신 공지 요약 정보를 조회한다.
     */
    @Transactional(readOnly = true)
    public HomeRepresentativeNoticeResponse getHomeLatestNotice(
            JwtAuthenticationPrincipal principal,
            Long workPlaceId
    ) {
        validateCanReadWorkPlace(principal, workPlaceId);
        HomeNoticeSummaryResponse notice = noticeRepository
                .findFirstByWorkPlace_IdAndStatusAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                        workPlaceId,
                        NoticeStatus.ACTIVE
                )
                .map(value -> HomeNoticeSummaryResponse.from(value, resolveWriterName(value.getWriterMemberId())))
                .orElse(null);
        return new HomeRepresentativeNoticeResponse(notice);
    }

    /**
     * 사업장에 접근 가능한 회원이 공지 단건을 조회한다.
     */
    @Transactional(readOnly = true)
    public NoticeResponse getNotice(JwtAuthenticationPrincipal principal, Long noticeId) {
        Notice notice = findActiveNotice(noticeId);
        validateCanReadWorkPlace(principal, notice.getWorkPlace().getId());
        return NoticeResponse.from(notice, resolveWriterName(notice.getWriterMemberId()));
    }

    /**
     * 사장님이 본인 사업장의 공지를 수정한다.
     */
    @Transactional
    public NoticeResponse updateNotice(Long ownerMemberId, Long noticeId, NoticeUpdateRequest request) {
        Member owner = findActiveMember(ownerMemberId);
        Notice notice = findActiveNotice(noticeId);
        validateOwnedActiveWorkPlace(notice.getWorkPlace().getId(), owner.getId());

        notice.update(request.title(), request.content(), request.representative());
        applyRepresentativePolicy(notice);

        return NoticeResponse.from(notice, owner.getName());
    }

    /**
     * 사장님이 본인 사업장의 공지를 삭제 상태로 변경한다.
     */
    @Transactional
    public void deleteNotice(Long ownerMemberId, Long noticeId) {
        Member owner = findActiveMember(ownerMemberId);
        Notice notice = findActiveNotice(noticeId);
        validateOwnedActiveWorkPlace(notice.getWorkPlace().getId(), owner.getId());
        notice.markDeleted(LocalDateTime.now());
    }

    /**
     * 사장님이 본인 사업장의 공지에 댓글을 작성한다.
     */
    @Transactional
    public NoticeCommentResponse createComment(Long ownerMemberId, Long noticeId, NoticeCommentRequest request) {
        Member owner = findActiveMember(ownerMemberId);
        Notice notice = findActiveNotice(noticeId);
        validateOwnedActiveWorkPlace(notice.getWorkPlace().getId(), owner.getId());

        NoticeComment comment = noticeCommentRepository.save(NoticeComment.create(
                notice,
                owner.getId(),
                request.content()
        ));
        return NoticeCommentResponse.from(comment, owner.getName());
    }

    /**
     * 사업장에 접근 가능한 회원이 공지 댓글을 커서 방식으로 조회한다.
     */
    @Transactional(readOnly = true)
    public NoticeCommentCursorResponse getComments(
            JwtAuthenticationPrincipal principal,
            Long noticeId,
            Long cursorId,
            int size
    ) {
        Notice notice = findActiveNotice(noticeId);
        validateCanReadWorkPlace(principal, notice.getWorkPlace().getId());

        long normalizedCursorId = cursorId == null ? 0L : cursorId;
        int normalizedSize = Math.min(size, MAX_COMMENT_SCROLL_SIZE);
        List<NoticeComment> comments = noticeCommentRepository
                .findByNotice_IdAndStatusAndDeletedAtIsNullAndIdGreaterThanOrderByIdAsc(
                        noticeId,
                        NoticeCommentStatus.ACTIVE,
                        normalizedCursorId,
                        PageRequest.of(0, normalizedSize + 1)
                );

        boolean hasNext = comments.size() > normalizedSize;
        List<NoticeComment> visibleComments = hasNext ? comments.subList(0, normalizedSize) : comments;
        Map<Long, String> writerNames = resolveCommentWriterNames(visibleComments);
        List<NoticeCommentResponse> content = visibleComments.stream()
                .map(comment -> NoticeCommentResponse.from(comment, writerNames.get(comment.getWriterMemberId())))
                .toList();
        Long nextCursorId = hasNext && !visibleComments.isEmpty()
                ? visibleComments.get(visibleComments.size() - 1).getId()
                : null;

        return new NoticeCommentCursorResponse(content, nextCursorId, hasNext);
    }

    /**
     * 사장님이 본인 사업장의 공지 댓글을 수정한다.
     */
    @Transactional
    public NoticeCommentResponse updateComment(
            Long ownerMemberId,
            Long noticeId,
            Long commentId,
            NoticeCommentRequest request
    ) {
        Member owner = findActiveMember(ownerMemberId);
        Notice notice = findActiveNotice(noticeId);
        validateOwnedActiveWorkPlace(notice.getWorkPlace().getId(), owner.getId());
        NoticeComment comment = findActiveComment(commentId, noticeId);

        comment.update(request.content());
        return NoticeCommentResponse.from(comment, owner.getName());
    }

    /**
     * 사장님이 본인 사업장의 공지 댓글을 삭제 상태로 변경한다.
     */
    @Transactional
    public void deleteComment(Long ownerMemberId, Long noticeId, Long commentId) {
        Member owner = findActiveMember(ownerMemberId);
        Notice notice = findActiveNotice(noticeId);
        validateOwnedActiveWorkPlace(notice.getWorkPlace().getId(), owner.getId());
        NoticeComment comment = findActiveComment(commentId, noticeId);
        comment.markDeleted(LocalDateTime.now());
    }

    /**
     * 대표 공지로 지정된 경우 같은 사업장의 기존 대표 공지를 해제한다.
     */
    private void applyRepresentativePolicy(Notice notice) {
        if (notice.isRepresentative()) {
            noticeRepository.unsetOtherRepresentatives(
                    notice.getWorkPlace().getId(),
                    notice.getId(),
                    NoticeStatus.ACTIVE
            );
        }
    }

    /**
     * 인증 주체가 활성 회원인지 확인한다.
     */
    private Member findActiveMember(Long memberId) {
        return memberRepository.findById(memberId)
                .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "인증 정보가 올바르지 않습니다."));
    }

    /**
     * 사장님이 소유한 활성 사업장을 조회한다.
     */
    private WorkPlace findOwnedActiveWorkPlace(Long workPlaceId, Long ownerMemberId) {
        return workPlaceRepository.findByIdAndOwnerMemberId(workPlaceId, ownerMemberId)
                .filter(workPlace -> workPlace.getStatus() == WorkPlaceStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "조회할 수 있는 사업장을 찾을 수 없습니다."));
    }

    /**
     * 사장님이 특정 활성 사업장을 소유하고 있는지 확인한다.
     */
    private void validateOwnedActiveWorkPlace(Long workPlaceId, Long ownerMemberId) {
        findOwnedActiveWorkPlace(workPlaceId, ownerMemberId);
    }

    /**
     * 활성 공지를 조회한다.
     */
    private Notice findActiveNotice(Long noticeId) {
        return noticeRepository.findByIdAndStatusAndDeletedAtIsNull(noticeId, NoticeStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "공지사항을 찾을 수 없습니다."));
    }

    /**
     * 활성 댓글을 조회한다.
     */
    private NoticeComment findActiveComment(Long commentId, Long noticeId) {
        return noticeCommentRepository.findByIdAndNotice_IdAndStatusAndDeletedAtIsNull(
                        commentId,
                        noticeId,
                        NoticeCommentStatus.ACTIVE
                )
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "공지 댓글을 찾을 수 없습니다."));
    }

    /**
     * 회원 역할에 따라 사업장 공지 읽기 권한을 확인한다.
     */
    private void validateCanReadWorkPlace(JwtAuthenticationPrincipal principal, Long workPlaceId) {
        if (principal.role() == MemberRole.OWNER) {
            validateOwnedActiveWorkPlace(workPlaceId, principal.memberId());
            return;
        }
        if (principal.role() == MemberRole.WORKER && hasApprovedCrewMembership(principal.memberId(), workPlaceId)) {
            return;
        }
        throw new ApiException(ErrorCode.FORBIDDEN, "사업장 공지에 접근할 권한이 없습니다.");
    }

    /**
     * 근무자가 승인된 활성 크루인지 확인한다.
     */
    private boolean hasApprovedCrewMembership(Long memberId, Long workPlaceId) {
        return crewRepository.existsByMember_IdAndWorkPlace_IdAndJoinStatusAndStatus(
                memberId,
                workPlaceId,
                CrewJoinStatus.APPROVED,
                CrewStatus.ACTIVE
        );
    }

    /**
     * 공지 작성자 이름을 일괄 조회한다.
     */
    private Map<Long, String> resolveWriterNames(List<Notice> notices) {
        List<Long> writerMemberIds = notices.stream()
                .map(Notice::getWriterMemberId)
                .distinct()
                .toList();
        return memberRepository.findAllById(writerMemberIds).stream()
                .collect(Collectors.toMap(Member::getId, Member::getName));
    }

    /**
     * 댓글 작성자 이름을 일괄 조회한다.
     */
    private Map<Long, String> resolveCommentWriterNames(List<NoticeComment> comments) {
        List<Long> writerMemberIds = comments.stream()
                .map(NoticeComment::getWriterMemberId)
                .distinct()
                .toList();
        return memberRepository.findAllById(writerMemberIds).stream()
                .collect(Collectors.toMap(Member::getId, Member::getName));
    }

    /**
     * 단일 작성자 이름을 조회한다.
     */
    private String resolveWriterName(Long writerMemberId) {
        return memberRepository.findById(writerMemberId)
                .map(Member::getName)
                .orElse(null);
    }
}
