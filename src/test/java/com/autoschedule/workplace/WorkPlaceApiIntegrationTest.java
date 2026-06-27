package com.autoschedule.workplace;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autoschedule.auth.jwt.JwtTokenProvider;
import com.autoschedule.crew.domain.Crew;
import com.autoschedule.crew.repository.CrewRepository;
import com.autoschedule.member.domain.Member;
import com.autoschedule.member.domain.MemberRole;
import com.autoschedule.member.domain.SocialProvider;
import com.autoschedule.member.repository.MemberRepository;
import com.autoschedule.workplace.domain.WorkPlace;
import com.autoschedule.workplace.domain.WorkPlaceSize;
import com.autoschedule.workplace.repository.WorkPlaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 로그인 회원의 홈 매장 선택 목록 API를 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class WorkPlaceApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private WorkPlaceRepository workPlaceRepository;

    @Autowired
    private CrewRepository crewRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Member owner;
    private Member worker;
    private Member outsiderWorker;

    /**
     * 각 테스트가 독립적으로 실행되도록 DB 상태를 초기화하고 기본 회원을 만든다.
     */
    @BeforeEach
    void setUp() {
        cleanupDatabase();

        owner = memberRepository.save(Member.create(
                SocialProvider.GOOGLE,
                "owner-subject",
                "owner@test.com",
                "owner",
                "01000000000",
                MemberRole.OWNER
        ));
        worker = memberRepository.save(Member.create(
                SocialProvider.KAKAO,
                "worker-subject",
                "worker@test.com",
                "worker",
                "01011111111",
                MemberRole.WORKER
        ));
        outsiderWorker = memberRepository.save(Member.create(
                SocialProvider.APPLE,
                "outsider-subject",
                "outsider@test.com",
                "outsider",
                "01022222222",
                MemberRole.WORKER
        ));
    }

    /**
     * 사장님은 본인이 승인된 OWNER 크루로 소속된 활성 매장 목록을 조회한다.
     */
    @Test
    void ownerReadsMyApprovedActiveWorkPlaces() throws Exception {
        WorkPlace first = createWorkPlace(owner, "매장명1");
        WorkPlace second = createWorkPlace(owner, "매장명2");
        crewRepository.save(Crew.createOwner(owner, first));
        crewRepository.save(Crew.createOwner(owner, second));

        mockMvc.perform(get("/api/work-places/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workPlaces.length()").value(2))
                .andExpect(jsonPath("$.workPlaces[0].workPlaceId").value(first.getId()))
                .andExpect(jsonPath("$.workPlaces[0].name").value("매장명1"))
                .andExpect(jsonPath("$.workPlaces[0].crewRole").value("OWNER"))
                .andExpect(jsonPath("$.workPlaces[0].joinStatus").value("APPROVED"))
                .andExpect(jsonPath("$.workPlaces[0].workPlaceStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.workPlaces[1].workPlaceId").value(second.getId()))
                .andExpect(jsonPath("$.workPlaces[1].name").value("매장명2"))
                .andExpect(jsonPath("$.workPlaces[1].crewRole").value("OWNER"));
    }

    /**
     * 근무자는 여러 매장에 승인된 WORKER 크루로 소속될 수 있고 그 목록을 한 번에 조회한다.
     */
    @Test
    void workerReadsMyApprovedActiveWorkPlaces() throws Exception {
        WorkPlace first = createWorkPlace(owner, "알바 매장1");
        WorkPlace second = createWorkPlace(owner, "알바 매장2");
        crewRepository.save(Crew.createOwner(owner, first));
        crewRepository.save(Crew.createOwner(owner, second));
        crewRepository.save(Crew.createWorker(worker, first));
        crewRepository.save(Crew.createWorker(worker, second));

        mockMvc.perform(get("/api/work-places/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workPlaces.length()").value(2))
                .andExpect(jsonPath("$.workPlaces[0].workPlaceId").value(first.getId()))
                .andExpect(jsonPath("$.workPlaces[0].name").value("알바 매장1"))
                .andExpect(jsonPath("$.workPlaces[0].crewRole").value("WORKER"))
                .andExpect(jsonPath("$.workPlaces[1].workPlaceId").value(second.getId()))
                .andExpect(jsonPath("$.workPlaces[1].name").value("알바 매장2"))
                .andExpect(jsonPath("$.workPlaces[1].crewRole").value("WORKER"));
    }

    /**
     * 승인되지 않았거나 비활성 처리된 소속과 비활성 매장은 홈 매장 목록에서 제외된다.
     */
    @Test
    void myWorkPlacesExcludeInactiveOrUnapprovedMembershipsAndInactiveWorkPlaces() throws Exception {
        WorkPlace active = createWorkPlace(owner, "활성 매장");
        WorkPlace pending = createWorkPlace(owner, "대기 매장");
        WorkPlace inactiveCrew = createWorkPlace(owner, "비활성 크루 매장");
        WorkPlace inactiveWorkPlace = createWorkPlace(owner, "비활성 매장");
        crewRepository.save(Crew.createOwner(owner, active));
        crewRepository.save(Crew.createWorker(worker, active));
        Crew pendingCrew = crewRepository.save(Crew.createWorker(worker, pending));
        Crew inactiveMembership = crewRepository.save(Crew.createWorker(worker, inactiveCrew));
        crewRepository.save(Crew.createWorker(worker, inactiveWorkPlace));
        markCrewJoinStatus(pendingCrew.getId(), "PENDING");
        markCrewStatusDeleted(inactiveMembership.getId());
        markWorkPlaceDeleted(inactiveWorkPlace.getId());

        mockMvc.perform(get("/api/work-places/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workPlaces.length()").value(1))
                .andExpect(jsonPath("$.workPlaces[0].workPlaceId").value(active.getId()))
                .andExpect(jsonPath("$.workPlaces[0].name").value("활성 매장"));
    }

    /**
     * 소속된 활성 매장이 없으면 빈 목록을 반환한다.
     */
    @Test
    void myWorkPlacesReturnsEmptyListWhenNoMembershipExists() throws Exception {
        mockMvc.perform(get("/api/work-places/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsiderWorker)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workPlaces.length()").value(0));
    }

    private WorkPlace createWorkPlace(Member ownerMember, String name) {
        return workPlaceRepository.save(WorkPlace.create(
                ownerMember.getId(),
                WorkPlaceSize.FIVE_TO_NINE,
                name,
                "서울시 강남구",
                "3층"
        ));
    }

    private void markCrewJoinStatus(Long crewId, String joinStatus) {
        jdbcTemplate.update(
                "update crew set join_status = ? where crew_id = ?",
                joinStatus,
                crewId
        );
    }

    private void markCrewStatusDeleted(Long crewId) {
        jdbcTemplate.update(
                "update crew set status = 'INACTIVE', deleted_at = now() where crew_id = ?",
                crewId
        );
    }

    private void markWorkPlaceDeleted(Long workPlaceId) {
        jdbcTemplate.update(
                "update work_place set status = 'INACTIVE', deleted_at = now() where work_place_id = ?",
                workPlaceId
        );
    }

    private String bearer(Member member) {
        return "Bearer " + jwtTokenProvider.issue(member).accessToken();
    }

    private void cleanupDatabase() {
        com.autoschedule.support.TestDatabaseCleaner.clean(jdbcTemplate);
    }
}
