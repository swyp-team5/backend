package com.autoschedule.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autoschedule.auth.domain.TokenType;
import com.autoschedule.auth.jwt.JwtTokenProvider;
import com.autoschedule.member.domain.Member;
import com.autoschedule.member.domain.MemberRole;
import com.autoschedule.member.domain.SocialProvider;
import com.autoschedule.member.repository.MemberRepository;
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
 * 회원 본인의 프로필 조회와 수정 API를 실제 MVC 흐름에서 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MemberProfileApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Member worker;

    /**
     * 각 테스트가 독립적으로 실행되도록 회원 데이터를 초기화한다.
     */
    @BeforeEach
    void setUp() {
        cleanupDatabase();
        worker = memberRepository.save(Member.create(
                SocialProvider.GOOGLE,
                "profile-worker-subject",
                "worker@test.com",
                "worker",
                "01020000000",
                MemberRole.WORKER
        ));
    }

    /**
     * 로그인 회원은 자신의 이름, 휴대폰 번호, 프로필 이미지 정보를 조회할 수 있다.
     */
    @Test
    void authenticatedMemberGetsOwnProfileWithoutImage() throws Exception {
        mockMvc.perform(get("/api/members/me/profile")
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(worker.getId()))
                .andExpect(jsonPath("$.name").value("worker"))
                .andExpect(jsonPath("$.phoneNumber").value("01020000000"))
                .andExpect(jsonPath("$.profileImage").doesNotExist());
    }

    /**
     * 로그인 회원은 이름과 휴대폰 번호를 수정할 수 있다.
     */
    @Test
    void authenticatedMemberUpdatesOwnProfile() throws Exception {
        mockMvc.perform(patch("/api/members/me/profile")
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "수정이름",
                                  "phoneNumber": "01099998888"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("수정이름"))
                .andExpect(jsonPath("$.phoneNumber").value("01099998888"));

        Member updatedMember = memberRepository.findById(worker.getId()).orElseThrow();
        assertThat(updatedMember.getName()).isEqualTo("수정이름");
        assertThat(updatedMember.getPhoneNumber()).isEqualTo("01099998888");
    }

    /**
     * 프로필 수정 요청의 이름과 휴대폰 번호가 정책에 맞지 않으면 필드 검증 오류를 반환한다.
     */
    @Test
    void profileUpdateValidatesNameAndPhoneNumber() throws Exception {
        mockMvc.perform(patch("/api/members/me/profile")
                        .header(HttpHeaders.AUTHORIZATION, bearer(worker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "",
                                  "phoneNumber": "010-9999-8888"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("4000"))
                .andExpect(jsonPath("$.errors[?(@.field == 'name')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'phoneNumber')]").exists());
    }

    /**
     * 인증되지 않은 사용자는 프로필 조회 API를 사용할 수 없다.
     */
    @Test
    void unauthenticatedMemberCannotGetProfile() throws Exception {
        mockMvc.perform(get("/api/members/me/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("4002"));
    }

    /**
     * 테스트용 access token 값을 생성한다.
     */
    private String bearer(Member member) {
        return "Bearer " + jwtTokenProvider.createToken(member, TokenType.ACCESS, 1800);
    }

    /**
     * 테스트 DB 데이터를 참조 순서에 맞춰 제거한다.
     */
    private void cleanupDatabase() {
        com.autoschedule.support.TestDatabaseCleaner.clean(jdbcTemplate);
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
