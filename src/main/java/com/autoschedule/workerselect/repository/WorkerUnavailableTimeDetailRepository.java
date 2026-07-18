package com.autoschedule.workerselect.repository;

import com.autoschedule.workerselect.domain.WorkerUnavailableTimeDetail;
import com.autoschedule.workerselect.domain.WorkerUnavailableTimeDetailStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 근무 불가능한 time_detail 목록을 조회하고 저장한다.
 */
public interface WorkerUnavailableTimeDetailRepository extends JpaRepository<WorkerUnavailableTimeDetail, Long> {

    List<WorkerUnavailableTimeDetail> findBySubmission_Id(Long submissionId);

    /**
     * 여러 제출 이력에 속한 근무 불가 time_detail 목록을 한 번에 조회한다.
     */
    List<WorkerUnavailableTimeDetail> findBySubmission_IdIn(Collection<Long> submissionIds);

    @Modifying(flushAutomatically = true)
    @Query("""
            update WorkerUnavailableTimeDetail detail
            set detail.status = :deletedStatus,
                detail.deletedAt = :deletedAt,
                detail.updatedAt = :deletedAt
            where detail.status = :activeStatus
              and detail.deletedAt is null
              and detail.submission.id in (
                  select submission.id
                  from WorkerSelectSubmission submission
                  where submission.workPlaceId = :workPlaceId
                    and submission.weekScheduleId = :weekScheduleId
              )
            """)
    int markActiveDeletedByWorkPlaceIdAndWeekScheduleId(
            @Param("workPlaceId") Long workPlaceId,
            @Param("weekScheduleId") Long weekScheduleId,
            @Param("activeStatus") WorkerUnavailableTimeDetailStatus activeStatus,
            @Param("deletedStatus") WorkerUnavailableTimeDetailStatus deletedStatus,
            @Param("deletedAt") LocalDateTime deletedAt
    );

    /**
     * 특정 제출 건에 속한 근무 불가 time_detail을 물리 삭제한다.
     * 반려 시 유니크 제약(work_place_id, week_schedule_id, member_id)을 비워 재제출을 가능하게 하기 위해 사용한다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from WorkerUnavailableTimeDetail detail where detail.submission.id = :submissionId")
    void deleteBySubmissionId(@Param("submissionId") Long submissionId);
}
