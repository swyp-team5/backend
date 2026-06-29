package com.autoschedule.notice.repository;

import com.autoschedule.notice.domain.NoticeImage;
import com.autoschedule.notice.domain.NoticeImageStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 공지 이미지 메타데이터 저장과 조회를 담당한다.
 */
public interface NoticeImageRepository extends JpaRepository<NoticeImage, Long> {

    /**
     * 공지에 연결된 활성 이미지를 노출 순서대로 조회한다.
     */
    List<NoticeImage> findByNotice_IdAndStatusAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(
            Long noticeId,
            NoticeImageStatus status
    );

    /**
     * 여러 공지에 연결된 활성 이미지를 한 번에 조회한다.
     */
    List<NoticeImage> findByNotice_IdInAndStatusAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(
            Collection<Long> noticeIds,
            NoticeImageStatus status
    );

    /**
     * 공지 작성자가 발급받은 대기 상태 이미지 업로드 이력을 조회한다.
     */
    Optional<NoticeImage> findByUploaderMemberIdAndObjectKeyAndStatusAndDeletedAtIsNull(
            Long uploaderMemberId,
            String objectKey,
            NoticeImageStatus status
    );

    /**
     * 공지에 연결된 활성 이미지 개수를 조회한다.
     */
    long countByNotice_IdAndStatusAndDeletedAtIsNull(Long noticeId, NoticeImageStatus status);
}
