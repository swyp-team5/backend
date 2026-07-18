package com.autoschedule.workplace.domain;

import com.autoschedule.global.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사장님이 생성한 사업장 기본 정보를 저장한다.
 */
@Getter
@Entity
@Table(name = "work_place")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkPlace extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "work_place_id")
    private Long id;

    @Column(name = "owner_member_id", nullable = false)
    private Long ownerMemberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WorkPlaceSize size;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "road_address", nullable = false, length = 255)
    private String roadAddress;

    @Column(name = "detail_address", length = 100)
    private String detailAddress;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkPlaceStatus status;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * 사장님 회원가입 과정에서 초기 사업장을 생성한다.
     */
    public static WorkPlace create(
            Long ownerMemberId,
            WorkPlaceSize size,
            String name,
            String roadAddress,
            String detailAddress
    ) {
        return create(ownerMemberId, size, name, roadAddress, detailAddress, null);
    }

    /**
     * 사장님이 관리할 사업장을 생성하고 선택 입력값인 매장 전화번호를 함께 저장한다.
     */
    public static WorkPlace create(
            Long ownerMemberId,
            WorkPlaceSize size,
            String name,
            String roadAddress,
            String detailAddress,
            String phoneNumber
    ) {
        WorkPlace workPlace = new WorkPlace();
        workPlace.ownerMemberId = ownerMemberId;
        workPlace.size = size;
        workPlace.name = name;
        workPlace.roadAddress = roadAddress;
        workPlace.detailAddress = detailAddress;
        workPlace.phoneNumber = phoneNumber;
        workPlace.status = WorkPlaceStatus.ACTIVE;
        return workPlace;
    }

    /**
     * 매장 전화번호 부가 정보를 추가, 수정 또는 삭제한다.
     */
    public void updatePhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

}
