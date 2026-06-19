package com.autoschedule.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoschedule.auth.domain.DevicePlatform;
import com.autoschedule.crew.domain.Crew;
import com.autoschedule.crew.repository.CrewRepository;
import com.autoschedule.member.domain.Member;
import com.autoschedule.member.domain.MemberRole;
import com.autoschedule.member.domain.MemberStatus;
import com.autoschedule.member.domain.SocialProvider;
import com.autoschedule.member.repository.MemberRepository;
import com.autoschedule.notification.domain.FcmToken;
import com.autoschedule.notification.repository.FcmTokenRepository;
import com.autoschedule.workplace.domain.WorkPlace;
import com.autoschedule.workplace.domain.WorkPlaceSize;
import com.autoschedule.workplace.repository.WorkPlaceRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 일반 API에서 사용할 활성 리소스 조회 Repository 계약을 검증한다.
 */
@SpringBootTest
class ActiveResourceLookupRepositoryIntegrationTest {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private WorkPlaceRepository workPlaceRepository;

    @Autowired
    private CrewRepository crewRepository;

    @Autowired
    private FcmTokenRepository fcmTokenRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 테스트 간 데이터가 섞이지 않도록 관련 테이블을 참조 역순으로 정리한다.
     */
    @BeforeEach
    void setUp() {
        cleanupDatabase();
    }

    /**
     * 활성 회원 조회는 ACTIVE 회원만 반환하고 탈퇴 유예/탈퇴 완료 회원은 없는 회원처럼 숨긴다.
     */
    @Test
    void findActiveMemberByIdReturnsOnlyActiveMember() {
        Member activeMember = saveMember("active-member", MemberRole.WORKER);
        Member pendingMember = saveMember("pending-member", MemberRole.WORKER);
        Member withdrawnMember = saveMember("withdrawn-member", MemberRole.WORKER);
        markMemberStatus(pendingMember.getId(), "WITHDRAWAL_PENDING");
        markMemberStatus(withdrawnMember.getId(), "WITHDRAWN");

        assertThat(memberRepository.findActiveById(activeMember.getId())).isPresent();
        assertThat(memberRepository.findActiveById(pendingMember.getId())).isEmpty();
        assertThat(memberRepository.findActiveById(withdrawnMember.getId())).isEmpty();
    }

    /**
     * 사장 소유 사업장 활성 조회는 ACTIVE이면서 삭제 시각이 없는 사업장만 반환한다.
     */
    @Test
    void findOwnedActiveWorkPlaceReturnsOnlyActiveAndNotDeletedWorkPlace() {
        Member owner = saveMember("owner-member", MemberRole.OWNER);
        WorkPlace activeWorkPlace = saveWorkPlace(owner.getId(), "active-store");
        WorkPlace inactiveWorkPlace = saveWorkPlace(owner.getId(), "inactive-store");
        WorkPlace deletedWorkPlace = saveWorkPlace(owner.getId(), "deleted-store");
        markWorkPlaceInactive(inactiveWorkPlace.getId());
        markWorkPlaceDeleted(deletedWorkPlace.getId());

        assertThat(workPlaceRepository.findOwnedActiveById(activeWorkPlace.getId(), owner.getId())).isPresent();
        assertThat(workPlaceRepository.findOwnedActiveById(inactiveWorkPlace.getId(), owner.getId())).isEmpty();
        assertThat(workPlaceRepository.findOwnedActiveById(deletedWorkPlace.getId(), owner.getId())).isEmpty();
    }

    /**
     * 활성 FCM 토큰 조회는 ACTIVE 토큰만 반환하고 비활성 토큰은 숨긴다.
     */
    @Test
    void findActiveFcmTokenReturnsOnlyActiveToken() {
        Member member = saveMember("fcm-member", MemberRole.WORKER);
        FcmToken activeToken = saveFcmToken(member, "active-device");
        FcmToken inactiveToken = saveFcmToken(member, "inactive-device");
        markFcmTokenInactive(inactiveToken.getId());

        assertThat(fcmTokenRepository.findActiveById(activeToken.getId())).isPresent();
        assertThat(fcmTokenRepository.findActiveById(inactiveToken.getId())).isEmpty();
        assertThat(fcmTokenRepository.findActiveByMemberIdAndDeviceId(member.getId(), "active-device")).isPresent();
        assertThat(fcmTokenRepository.findActiveByMemberIdAndDeviceId(member.getId(), "inactive-device")).isEmpty();
    }

    /**
     * 활성 크루 소속 존재 여부는 ACTIVE 크루만 중복 소속으로 판단한다.
     */
    @Test
    void existsActiveCrewMembershipIgnoresInactiveCrew() {
        Member worker = saveMember("crew-worker", MemberRole.WORKER);
        Member owner = saveMember("crew-owner", MemberRole.OWNER);
        WorkPlace workPlace = saveWorkPlace(owner.getId(), "crew-store");
        Crew activeCrew = crewRepository.save(Crew.createWorker(worker, workPlace));

        assertThat(crewRepository.existsActiveByMemberIdAndWorkPlaceId(worker.getId(), workPlace.getId())).isTrue();

        markCrewInactive(activeCrew.getId());

        assertThat(crewRepository.existsActiveByMemberIdAndWorkPlaceId(worker.getId(), workPlace.getId())).isFalse();
    }

    /**
     * 테스트 회원을 저장한다.
     */
    private Member saveMember(String socialSubject, MemberRole role) {
        return memberRepository.save(Member.create(
                SocialProvider.GOOGLE,
                socialSubject,
                socialSubject + "@test.com",
                "테스터",
                "01012345678",
                role
        ));
    }

    /**
     * 테스트 사업장을 저장한다.
     */
    private WorkPlace saveWorkPlace(Long ownerMemberId, String name) {
        return workPlaceRepository.save(WorkPlace.create(
                ownerMemberId,
                WorkPlaceSize.ONE_TO_FOUR,
                name,
                "서울시 강남구",
                "1층"
        ));
    }

    /**
     * 테스트 FCM 토큰을 저장한다.
     */
    private FcmToken saveFcmToken(Member member, String deviceId) {
        return fcmTokenRepository.save(FcmToken.create(
                member,
                deviceId,
                "token-" + deviceId,
                DevicePlatform.ANDROID,
                "1.0.0",
                LocalDateTime.now()
        ));
    }

    /**
     * 회원 상태를 직접 변경해 조회 정책 테스트 데이터를 준비한다.
     */
    private void markMemberStatus(Long memberId, String status) {
        jdbcTemplate.update(
                "update member set status = ?, deleted_at = ? where member_id = ?",
                status,
                LocalDateTime.now(),
                memberId
        );
    }

    /**
     * 사업장을 비활성 상태로 변경한다.
     */
    private void markWorkPlaceInactive(Long workPlaceId) {
        jdbcTemplate.update("update work_place set status = 'INACTIVE' where work_place_id = ?", workPlaceId);
    }

    /**
     * 사업장 삭제 시각을 기록한다.
     */
    private void markWorkPlaceDeleted(Long workPlaceId) {
        jdbcTemplate.update("update work_place set deleted_at = ? where work_place_id = ?", LocalDateTime.now(), workPlaceId);
    }

    /**
     * FCM 토큰을 비활성 상태로 변경한다.
     */
    private void markFcmTokenInactive(Long fcmTokenId) {
        jdbcTemplate.update(
                "update fcm_token set status = 'INACTIVE', deleted_at = ? where fcm_token_id = ?",
                LocalDateTime.now(),
                fcmTokenId
        );
    }

    /**
     * 크루 소속을 비활성 상태로 변경한다.
     */
    private void markCrewInactive(Long crewId) {
        jdbcTemplate.update(
                "update crew set status = 'INACTIVE', deleted_at = ? where crew_id = ?",
                LocalDateTime.now(),
                crewId
        );
    }

    /**
     * 테스트 DB 데이터를 참조 역순으로 삭제한다.
     */
    private void cleanupDatabase() {
        if (tableExists("notification_delivery")) {
            jdbcTemplate.update("delete from notification_delivery");
        }
        if (tableExists("notification")) {
            jdbcTemplate.update("delete from notification");
        }
        if (tableExists("fcm_token")) {
            jdbcTemplate.update("delete from fcm_token");
        }
        if (tableExists("notice_comment")) {
            jdbcTemplate.update("delete from notice_comment");
        }
        if (tableExists("notice")) {
            jdbcTemplate.update("delete from notice");
        }
        if (tableExists("crew_invitation")) {
            jdbcTemplate.update("delete from crew_invitation");
        }
        if (tableExists("crew")) {
            jdbcTemplate.update("delete from crew");
        }
        if (tableExists("member_terms_agreement")) {
            jdbcTemplate.update("delete from member_terms_agreement");
        }
        if (tableExists("work_place")) {
            jdbcTemplate.update("delete from work_place");
        }
        if (tableExists("member")) {
            jdbcTemplate.update("delete from member");
        }
    }

    /**
     * 현재 테스트 스키마에 테이블이 존재하는지 확인한다.
     */
    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        select count(*)
                          from information_schema.tables
                         where table_schema = database()
                           and table_name = ?
                        """,
                Integer.class,
                tableName
        );
        return count != null && count > 0;
    }
}
