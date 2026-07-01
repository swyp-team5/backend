package com.autoschedule.crew.domain;

import com.autoschedule.global.domain.BaseEntity;
import com.autoschedule.member.domain.Member;
import com.autoschedule.workplace.domain.WorkPlace;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원이 특정 사업장에 어떤 역할로 소속되어 있는지 저장한다.
 */
@Getter
@Entity
@Table(name = "crew")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Crew extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "crew_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_place_id", nullable = false)
    private WorkPlace workPlace;

    @Enumerated(EnumType.STRING)
    @Column(name = "join_status", nullable = false, length = 20)
    private CrewJoinStatus joinStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "crew_role", nullable = false, length = 20)
    private CrewRole crewRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CrewStatus status;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * 사업장을 생성한 사장님을 승인된 소속으로 등록한다.
     */
    public static Crew createOwner(Member member, WorkPlace workPlace) {
        Crew crew = new Crew();
        crew.member = member;
        crew.workPlace = workPlace;
        crew.joinStatus = CrewJoinStatus.APPROVED;
        crew.crewRole = CrewRole.OWNER;
        crew.status = CrewStatus.ACTIVE;
        return crew;
    }

    /**
     * 초대 코드 수락이 완료된 근무자를 사업장 크루로 등록한다.
     */
    public static Crew createWorker(Member member, WorkPlace workPlace) {
        Crew crew = new Crew();
        crew.member = member;
        crew.workPlace = workPlace;
        crew.joinStatus = CrewJoinStatus.APPROVED;
        crew.crewRole = CrewRole.WORKER;
        crew.status = CrewStatus.ACTIVE;
        return crew;
    }

    /**
     * 비활성 처리된 근무자 소속 이력을 다시 활성 크루로 복구한다.
     */
    public void reactivateWorker() {
        this.joinStatus = CrewJoinStatus.APPROVED;
        this.crewRole = CrewRole.WORKER;
        this.status = CrewStatus.ACTIVE;
        this.deletedAt = null;
    }

    /**
     * 사업장에서 근무자 크루를 비활성 처리한다.
     */
    public void deactivate(LocalDateTime deletedAt) {
        this.status = CrewStatus.INACTIVE;
        this.deletedAt = deletedAt;
    }

}
