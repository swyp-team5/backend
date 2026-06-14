package com.autoschedule.notice.controller;

import com.autoschedule.auth.jwt.JwtAuthenticationPrincipal;
import com.autoschedule.global.security.annotation.OwnerOnly;
import com.autoschedule.notice.dto.HomeRepresentativeNoticeResponse;
import com.autoschedule.notice.dto.NoticeCommentCursorResponse;
import com.autoschedule.notice.dto.NoticeCommentRequest;
import com.autoschedule.notice.dto.NoticeCommentResponse;
import com.autoschedule.notice.dto.NoticeCreateRequest;
import com.autoschedule.notice.dto.NoticePageResponse;
import com.autoschedule.notice.dto.NoticeResponse;
import com.autoschedule.notice.dto.NoticeUpdateRequest;
import com.autoschedule.notice.dto.RepresentativeNoticeResponse;
import com.autoschedule.notice.service.NoticeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사업장 공지와 댓글 API 요청을 처리한다.
 */
@Validated
@RestController
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeService noticeService;

    /**
     * 사장님이 사업장 공지를 작성한다.
     */
    @OwnerOnly
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/api/work-places/{workPlaceId}/notices")
    public NoticeResponse createNotice(
            @AuthenticationPrincipal JwtAuthenticationPrincipal principal,
            @PathVariable Long workPlaceId,
            @Valid @RequestBody NoticeCreateRequest request
    ) {
        return noticeService.createNotice(principal.memberId(), workPlaceId, request);
    }

    /**
     * 사업장 공지 목록을 페이지 방식으로 조회한다.
     */
    @GetMapping("/api/work-places/{workPlaceId}/notices")
    public NoticePageResponse getNotices(
            @AuthenticationPrincipal JwtAuthenticationPrincipal principal,
            @PathVariable Long workPlaceId,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
            @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.") int size
    ) {
        return noticeService.getNotices(principal, workPlaceId, page, size);
    }

    /**
     * 사업장 대표 공지를 조회한다.
     */
    @GetMapping("/api/work-places/{workPlaceId}/notices/representative")
    public RepresentativeNoticeResponse getRepresentativeNotice(
            @AuthenticationPrincipal JwtAuthenticationPrincipal principal,
            @PathVariable Long workPlaceId
    ) {
        return noticeService.getRepresentativeNotice(principal, workPlaceId);
    }

    /**
     * 홈 화면에 표시할 사업장 대표 공지를 조회한다.
     */
    @GetMapping("/api/home/work-places/{workPlaceId}/representative-notice")
    public HomeRepresentativeNoticeResponse getHomeRepresentativeNotice(
            @AuthenticationPrincipal JwtAuthenticationPrincipal principal,
            @PathVariable Long workPlaceId
    ) {
        return noticeService.getHomeRepresentativeNotice(principal, workPlaceId);
    }

    /**
     * 홈 화면에 표시할 사업장 최신 공지를 조회한다.
     */
    @GetMapping("/api/home/work-places/{workPlaceId}/latest-notice")
    public HomeRepresentativeNoticeResponse getHomeLatestNotice(
            @AuthenticationPrincipal JwtAuthenticationPrincipal principal,
            @PathVariable Long workPlaceId
    ) {
        return noticeService.getHomeLatestNotice(principal, workPlaceId);
    }

    /**
     * 공지 단건을 조회한다.
     */
    @GetMapping("/api/notices/{noticeId}")
    public NoticeResponse getNotice(
            @AuthenticationPrincipal JwtAuthenticationPrincipal principal,
            @PathVariable Long noticeId
    ) {
        return noticeService.getNotice(principal, noticeId);
    }

    /**
     * 사장님이 공지를 수정한다.
     */
    @OwnerOnly
    @PatchMapping("/api/notices/{noticeId}")
    public NoticeResponse updateNotice(
            @AuthenticationPrincipal JwtAuthenticationPrincipal principal,
            @PathVariable Long noticeId,
            @Valid @RequestBody NoticeUpdateRequest request
    ) {
        return noticeService.updateNotice(principal.memberId(), noticeId, request);
    }

    /**
     * 사장님이 공지를 삭제한다.
     */
    @OwnerOnly
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/api/notices/{noticeId}")
    public void deleteNotice(
            @AuthenticationPrincipal JwtAuthenticationPrincipal principal,
            @PathVariable Long noticeId
    ) {
        noticeService.deleteNotice(principal.memberId(), noticeId);
    }

    /**
     * 사장님이 공지 댓글을 작성한다.
     */
    @OwnerOnly
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/api/notices/{noticeId}/comments")
    public NoticeCommentResponse createComment(
            @AuthenticationPrincipal JwtAuthenticationPrincipal principal,
            @PathVariable Long noticeId,
            @Valid @RequestBody NoticeCommentRequest request
    ) {
        return noticeService.createComment(principal.memberId(), noticeId, request);
    }

    /**
     * 공지 댓글을 커서 방식으로 조회한다.
     */
    @GetMapping("/api/notices/{noticeId}/comments")
    public NoticeCommentCursorResponse getComments(
            @AuthenticationPrincipal JwtAuthenticationPrincipal principal,
            @PathVariable Long noticeId,
            @RequestParam(required = false) @Positive(message = "댓글 커서는 1 이상이어야 합니다.") Long cursorId,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "조회 개수는 1 이상이어야 합니다.")
            @Max(value = 100, message = "조회 개수는 100 이하여야 합니다.") int size
    ) {
        return noticeService.getComments(principal, noticeId, cursorId, size);
    }

    /**
     * 사장님이 공지 댓글을 수정한다.
     */
    @OwnerOnly
    @PatchMapping("/api/notices/{noticeId}/comments/{commentId}")
    public NoticeCommentResponse updateComment(
            @AuthenticationPrincipal JwtAuthenticationPrincipal principal,
            @PathVariable Long noticeId,
            @PathVariable Long commentId,
            @Valid @RequestBody NoticeCommentRequest request
    ) {
        return noticeService.updateComment(principal.memberId(), noticeId, commentId, request);
    }

    /**
     * 사장님이 공지 댓글을 삭제한다.
     */
    @OwnerOnly
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/api/notices/{noticeId}/comments/{commentId}")
    public void deleteComment(
            @AuthenticationPrincipal JwtAuthenticationPrincipal principal,
            @PathVariable Long noticeId,
            @PathVariable Long commentId
    ) {
        noticeService.deleteComment(principal.memberId(), noticeId, commentId);
    }
}
