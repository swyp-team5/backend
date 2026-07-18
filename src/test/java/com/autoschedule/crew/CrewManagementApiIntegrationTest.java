package com.autoschedule.crew;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autoschedule.auth.jwt.JwtTokenProvider;
import com.autoschedule.crew.domain.Crew;
import com.autoschedule.crew.repository.CrewRepository;
import com.autoschedule.member.domain.Member;
import com.autoschedule.member.domain.MemberRole;
import com.autoschedule.member.domain.ProfileImage;
import com.autoschedule.member.domain.SocialProvider;
import com.autoschedule.member.repository.MemberRepository;
import com.autoschedule.member.repository.ProfileImageRepository;
import com.autoschedule.workplace.domain.WorkPlace;
import com.autoschedule.workplace.domain.WorkPlaceSize;
import com.autoschedule.workplace.repository.WorkPlaceRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 사업장 근무자 목록 조회와 근무자 삭제 API의 권한, 개인정보 노출 범위, 상태 변경을 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CrewManagementApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProfileImageRepository profileImageRepository;

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
    private Member secondWorker;
    private Member outsiderWorker;
    private WorkPlace workPlace;

    /**
     * 각 테스트가 독립적으로 실행되도록 DB를 초기화하고 기본 사업장과 크루를 만든다.
     */
    @BeforeEach
    void setUp() {
        cleanupDatabase();

        owner = saveMember("owner-subject", "owner", "01000000000", MemberRole.OWNER);
        worker = saveMember("worker-subject", "worker1", "01011111111", MemberRole.WORKER);
        secondWorker = saveMember("second-worker-subject", "worker2", "01022222222", MemberRole.WORKER);
        outsiderWorker = saveMember("outsider-worker-subject", "worker3", "01033333333", MemberRole.WORKER);
        workPlace = workPlaceRepository.save(WorkPlace.create(
                owner.getId(),
                WorkPlaceSize.FIVE_TO_NINE,
                "store",
                "road",
                "3F"
        ));
        crewRepository.save(Crew.createOwner(owner, workPlace));
        crewRepository.save(Crew.createWorker(worker, workPlace));
        crewRepository.save(Crew.createWorker(secondWorker, workPlace));
    }

    /**
     * 사장님은 본인 사업장의 근무자 개인정보와 프로필 이미지를 함께 조회할 수 있다.
     */
    @Test
    void ownerReadsWorkerCrewsWithPrivateInformation() throws Exception {
        saveActiveProfileImage(worker, "https://static.example.com/worker1.png");

        mockMvc.perform(get("/api/work-places/{workPlaceId}/crews", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.crews.length()").value(2))
                .andExpect(jsonPath("$.crews[0].memberId").value(worker.getId()))
                .andExpect(jsonPath("$.crews[0].name").value("worker1"))
                .andExpect(jsonPath("$.crews[0].phoneNumber").value("01011111111"))
                .andExpect(jsonPath("$.crews[0].profileImageUrl")
                        .value("https://static.example.com/worker1.png"))
                .andExpect(jsonPath("$.crews[0].crewRole").value("WORKER"))
                .andExpect(jsonPath("$.crews[0].joinStatus").value("APPROVED"))
                .andExpect(jsonPath("$.crews[0].crewStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.crews[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.crews[1].memberId").value(secondWorker.getId()))
                .andExpect(jsonPath("$.crews[1].phoneNumber").value("01022222222"))
                .andExpect(jsonPath("$.crews[1].profileImageUrl").value(nullValue()));
    }

    /**
     * 근무자는 같은 사업장의 근무자 이름과 프로필 이미지만 조회하고 휴대폰 번호는 볼 수 없다.
     */
    @Test
    void workerReadsWorkerCrewsWithoutPrivateInformation() throws Exception {
        saveActiveProfileImage(secondWorker, "https://static.example.com/worker2.png");

        mockMvc.perform(get("/api/work-places/{workPlaceId}/crews", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.crews.length()").value(2))
                .andExpect(jsonPath("$.crews[0].memberId").value(worker.getId()))
                .andExpect(jsonPath("$.crews[0].name").value("worker1"))
                .andExpect(jsonPath("$.crews[0].profileImageUrl").value(nullValue()))
                .andExpect(jsonPath("$.crews[0].phoneNumber").doesNotExist())
                .andExpect(jsonPath("$.crews[0].joinStatus").doesNotExist())
                .andExpect(jsonPath("$.crews[1].memberId").value(secondWorker.getId()))
                .andExpect(jsonPath("$.crews[1].name").value("worker2"))
                .andExpect(jsonPath("$.crews[1].profileImageUrl")
                        .value("https://static.example.com/worker2.png"))
                .andExpect(jsonPath("$.crews[1].phoneNumber").doesNotExist());
    }

    /**
     * 사업장에 소속되지 않은 근무자는 해당 사업장의 근무자 목록을 조회할 수 없다.
     */
    @Test
    void outsiderWorkerCannotReadWorkerCrews() throws Exception {
        mockMvc.perform(get("/api/work-places/{workPlaceId}/crews", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsiderWorker)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("4003"));
    }

    /**
     * 사장님은 본인 사업장의 근무자를 삭제해 활성 크루 목록에서 제외할 수 있다.
     */
    @Test
    void ownerDeletesWorkerCrewFromOwnWorkPlace() throws Exception {
        Crew targetCrew = crewRepository.findByMember_IdAndWorkPlace_Id(worker.getId(), workPlace.getId()).orElseThrow();

        mockMvc.perform(delete(
                        "/api/work-places/{workPlaceId}/crews/{crewId}",
                        workPlace.getId(),
                        targetCrew.getId()
                ).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNoContent());

        String status = jdbcTemplate.queryForObject(
                "select status from crew where crew_id = ?",
                String.class,
                targetCrew.getId()
        );
        LocalDateTime deletedAt = jdbcTemplate.queryForObject(
                "select deleted_at from crew where crew_id = ?",
                LocalDateTime.class,
                targetCrew.getId()
        );
        assertThat(status).isEqualTo("INACTIVE");
        assertThat(deletedAt).isNotNull();

        mockMvc.perform(get("/api/work-places/{workPlaceId}/crews", workPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.crews.length()").value(1))
                .andExpect(jsonPath("$.crews[0].memberId").value(secondWorker.getId()));
    }

    /**
     * 사장님은 본인 사업장의 OWNER 크루를 근무자 삭제 API로 삭제할 수 없다.
     */
    @Test
    void ownerCannotDeleteOwnerCrew() throws Exception {
        Crew ownerCrew = crewRepository.findByMember_IdAndWorkPlace_Id(owner.getId(), workPlace.getId()).orElseThrow();

        mockMvc.perform(delete(
                        "/api/work-places/{workPlaceId}/crews/{crewId}",
                        workPlace.getId(),
                        ownerCrew.getId()
                ).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("4001"));
    }

    /**
     * 근무자는 사업장의 근무자를 삭제할 수 없다.
     */
    @Test
    void workerCannotDeleteCrew() throws Exception {
        Crew targetCrew = crewRepository.findByMember_IdAndWorkPlace_Id(secondWorker.getId(), workPlace.getId())
                .orElseThrow();

        mockMvc.perform(delete(
                        "/api/work-places/{workPlaceId}/crews/{crewId}",
                        workPlace.getId(),
                        targetCrew.getId()
                ).header(HttpHeaders.AUTHORIZATION, bearer(worker)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("4003"));
    }

    private Member saveMember(String subject, String name, String phoneNumber, MemberRole role) {
        return memberRepository.save(Member.create(
                SocialProvider.GOOGLE,
                subject,
                subject + "@test.com",
                name,
                phoneNumber,
                role
        ));
    }

    private void saveActiveProfileImage(Member member, String imageUrl) {
        ProfileImage profileImage = ProfileImage.createPending(
                member,
                member.getName() + ".png",
                member.getName() + "-stored.png",
                "profile-images/" + member.getId() + "/" + member.getName() + ".png",
                imageUrl,
                "image/png",
                1024L
        );
        profileImage.activate("image/png", 1024L, LocalDateTime.now());
        profileImageRepository.save(profileImage);
    }

    private String bearer(Member member) {
        return "Bearer " + jwtTokenProvider.issue(member).accessToken();
    }

    private void cleanupDatabase() {
        com.autoschedule.support.TestDatabaseCleaner.clean(jdbcTemplate);
    }
}
