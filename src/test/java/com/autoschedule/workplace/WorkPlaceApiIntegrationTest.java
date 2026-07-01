package com.autoschedule.workplace;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 사업장 조회, 추가 생성, 전화번호 수정 API를 검증한다.
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

    /**
     * 사장님이 로그인 후 추가 사업장을 생성하면 사업장과 OWNER 크루 소속이 함께 생성된다.
     */
    @Test
    void ownerCreatesAdditionalWorkPlaceWithOwnerCrew() throws Exception {
        String response = mockMvc.perform(post("/api/work-places")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "size": "FIVE_TO_NINE",
                                  "name": "스위프 2호점",
                                  "roadAddress": "서울시 강남구 테헤란로 1",
                                  "detailAddress": "3층",
                                  "phoneNumber": "0212345678"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.workPlaceId").isNumber())
                .andExpect(jsonPath("$.name").value("스위프 2호점"))
                .andExpect(jsonPath("$.size").value("FIVE_TO_NINE"))
                .andExpect(jsonPath("$.roadAddress").value("서울시 강남구 테헤란로 1"))
                .andExpect(jsonPath("$.detailAddress").value("3층"))
                .andExpect(jsonPath("$.phoneNumber").value("0212345678"))
                .andExpect(jsonPath("$.ownerMemberId").value(owner.getId()))
                .andExpect(jsonPath("$.crewId").isNumber())
                .andExpect(jsonPath("$.crewRole").value("OWNER"))
                .andExpect(jsonPath("$.joinStatus").value("APPROVED"))
                .andExpect(jsonPath("$.crewStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.workPlaceStatus").value("ACTIVE"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Number workPlaceId = com.jayway.jsonpath.JsonPath.read(response, "$.workPlaceId");
        Integer ownerCrewCount = jdbcTemplate.queryForObject(
                """
                        select count(*)
                          from crew
                         where member_id = ?
                           and work_place_id = ?
                           and crew_role = 'OWNER'
                           and join_status = 'APPROVED'
                           and status = 'ACTIVE'
                        """,
                Integer.class,
                owner.getId(),
                workPlaceId.longValue()
        );
        org.assertj.core.api.Assertions.assertThat(ownerCrewCount).isEqualTo(1);
    }

    /**
     * 사업장 전화번호는 부가 정보이므로 추가 사업장 생성 시 생략할 수 있다.
     */
    @Test
    void ownerCreatesAdditionalWorkPlaceWithoutPhoneNumber() throws Exception {
        mockMvc.perform(post("/api/work-places")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "size": "ONE_TO_FOUR",
                                  "name": "전화번호 없는 매장",
                                  "roadAddress": "서울시 강남구 테헤란로 2",
                                  "detailAddress": null
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("전화번호 없는 매장"))
                .andExpect(jsonPath("$.phoneNumber").value(nullValue()))
                .andExpect(jsonPath("$.crewRole").value("OWNER"));
    }

    /**
     * 근무자 계정은 사업장을 직접 추가할 수 없다.
     */
    @Test
    void workerCannotCreateAdditionalWorkPlace() throws Exception {
        mockMvc.perform(post("/api/work-places")
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "size": "FIVE_TO_NINE",
                                  "name": "근무자 매장",
                                  "roadAddress": "서울시 강남구 테헤란로 3",
                                  "phoneNumber": "0212345678"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    /**
     * 사장님은 본인 사업장의 전화번호를 추가하거나 수정할 수 있다.
     */
    @Test
    void ownerUpdatesOwnWorkPlacePhoneNumber() throws Exception {
        WorkPlace workPlace = createWorkPlace(owner, "전화번호 수정 매장");
        crewRepository.save(Crew.createOwner(owner, workPlace));

        mockMvc.perform(patch("/api/work-places/{workPlaceId}/phone-number", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "phoneNumber": "15881234"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workPlaceId").value(workPlace.getId()))
                .andExpect(jsonPath("$.phoneNumber").value("15881234"));
    }

    /**
     * 사장님은 본인 사업장의 전화번호를 null로 수정해 부가 정보를 삭제할 수 있다.
     */
    @Test
    void ownerClearsOwnWorkPlacePhoneNumber() throws Exception {
        WorkPlace workPlace = createWorkPlace(owner, "전화번호 삭제 매장");
        crewRepository.save(Crew.createOwner(owner, workPlace));
        jdbcTemplate.update(
                "update work_place set phone_number = '0212345678' where work_place_id = ?",
                workPlace.getId()
        );

        mockMvc.perform(patch("/api/work-places/{workPlaceId}/phone-number", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "phoneNumber": null
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workPlaceId").value(workPlace.getId()))
                .andExpect(jsonPath("$.phoneNumber").value(nullValue()));
    }

    /**
     * 사장님은 다른 사장님의 사업장 전화번호를 수정할 수 없다.
     */
    @Test
    void ownerCannotUpdateOtherOwnersWorkPlacePhoneNumber() throws Exception {
        Member otherOwner = memberRepository.save(Member.create(
                SocialProvider.GOOGLE,
                "other-owner-subject",
                "other-owner@test.com",
                "other",
                "01033333333",
                MemberRole.OWNER
        ));
        WorkPlace otherWorkPlace = createWorkPlace(otherOwner, "다른 사장 매장");
        crewRepository.save(Crew.createOwner(otherOwner, otherWorkPlace));

        mockMvc.perform(patch("/api/work-places/{workPlaceId}/phone-number", otherWorkPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "phoneNumber": "0212345678"
                                }
                                """))
                .andExpect(status().isNotFound());
    }

    /**
     * 매장 목록 조회 응답에는 부가 정보인 사업장 전화번호가 포함된다.
     */
    @Test
    void myWorkPlacesIncludePhoneNumber() throws Exception {
        WorkPlace workPlace = createWorkPlace(owner, "전화번호 있는 매장");
        crewRepository.save(Crew.createOwner(owner, workPlace));
        jdbcTemplate.update(
                "update work_place set phone_number = '0212345678' where work_place_id = ?",
                workPlace.getId()
        );

        mockMvc.perform(get("/api/work-places/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workPlaces[0].workPlaceId").value(workPlace.getId()))
                .andExpect(jsonPath("$.workPlaces[0].phoneNumber").value("0212345678"));
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
