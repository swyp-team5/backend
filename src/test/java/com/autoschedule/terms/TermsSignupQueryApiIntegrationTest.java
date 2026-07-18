package com.autoschedule.terms;

import static org.hamcrest.Matchers.containsInRelativeOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autoschedule.terms.domain.Terms;
import com.autoschedule.terms.domain.TermsStatus;
import com.autoschedule.terms.domain.TermsType;
import com.autoschedule.terms.repository.TermsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 회원가입 약관 조회 API가 역할별 활성 약관과 공개 접근 정책을 만족하는지 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TermsSignupQueryApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TermsRepository termsRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 각 테스트가 독립적으로 약관 데이터를 구성할 수 있도록 약관 관련 테이블을 비운다.
     */
    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        jdbcTemplate.update("DELETE FROM member_terms_agreement");
        jdbcTemplate.update("DELETE FROM terms");
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
    }

    /**
     * 사장님 회원가입 약관 조회는 인증 없이 COMMON, OWNER 활성 약관만 반환한다.
     */
    @Test
    void ownerSignupTermsReturnsCommonAndOwnerActiveTermsWithoutAuthentication() throws Exception {
        saveTerms(TermsType.COMMON, "공통 필수", true, TermsStatus.ACTIVE, "공통 본문", "1.0.0");
        saveTerms(TermsType.OWNER, "사장 필수", true, TermsStatus.ACTIVE, "사장 본문", "1.0.0");
        saveTerms(TermsType.WORKER, "근무자 필수", true, TermsStatus.ACTIVE, "근무자 본문", "1.0.0");
        saveTerms(TermsType.OWNER, "비활성 사장", true, TermsStatus.INACTIVE, "비활성 본문", "1.0.0");

        mockMvc.perform(get("/api/terms/signup")
                        .param("role", "OWNER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.terms.length()").value(2))
                .andExpect(jsonPath("$.terms[*].termsType").value(containsInRelativeOrder("COMMON", "OWNER")))
                .andExpect(jsonPath("$.terms[*].title").value(hasItem("공통 필수")))
                .andExpect(jsonPath("$.terms[*].title").value(hasItem("사장 필수")))
                .andExpect(jsonPath("$.terms[*].title").value(not(hasItem("근무자 필수"))))
                .andExpect(jsonPath("$.terms[*].title").value(not(hasItem("비활성 사장"))))
                .andExpect(jsonPath("$.terms[0].termsId").isNumber())
                .andExpect(jsonPath("$.terms[0].required").value(true))
                .andExpect(jsonPath("$.terms[0].version").value("1.0.0"))
                .andExpect(jsonPath("$.terms[0].content").value("공통 본문"));
    }

    /**
     * 근무자 회원가입 약관 조회는 COMMON, WORKER 활성 약관만 반환한다.
     */
    @Test
    void workerSignupTermsReturnsCommonAndWorkerActiveTerms() throws Exception {
        saveTerms(TermsType.COMMON, "공통 필수", true, TermsStatus.ACTIVE, "공통 본문", "1.0.0");
        saveTerms(TermsType.OWNER, "사장 필수", true, TermsStatus.ACTIVE, "사장 본문", "1.0.0");
        saveTerms(TermsType.WORKER, "근무자 선택", false, TermsStatus.ACTIVE, "근무자 본문", "1.0.0");

        mockMvc.perform(get("/api/terms/signup")
                        .param("role", "WORKER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.terms.length()").value(2))
                .andExpect(jsonPath("$.terms[*].termsType").value(containsInRelativeOrder("COMMON", "WORKER")))
                .andExpect(jsonPath("$.terms[*].title").value(hasItem("공통 필수")))
                .andExpect(jsonPath("$.terms[*].title").value(hasItem("근무자 선택")))
                .andExpect(jsonPath("$.terms[*].title").value(not(hasItem("사장 필수"))))
                .andExpect(jsonPath("$.terms[1].required").value(false));
    }

    /**
     * role 파라미터가 없으면 전역 검증 오류 응답으로 거절한다.
     */
    @Test
    void signupTermsRejectsMissingRole() throws Exception {
        mockMvc.perform(get("/api/terms/signup"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("4000"))
                .andExpect(jsonPath("$.errors[?(@.field == 'role')].message")
                        .value(hasItem("회원가입 역할은 필수입니다.")));
    }

    /**
     * 지원하지 않는 role 값은 400 응답으로 거절한다.
     */
    @Test
    void signupTermsRejectsUnsupportedRole() throws Exception {
        mockMvc.perform(get("/api/terms/signup")
                        .param("role", "ADMIN"))
                .andExpect(status().isBadRequest());
    }

    /**
     * 테스트에 필요한 약관 엔티티를 저장한다.
     */
    private void saveTerms(
            TermsType termsType,
            String title,
            boolean required,
            TermsStatus status,
            String content,
            String version
    ) {
        termsRepository.save(Terms.create(termsType, title, required, status, content, version));
    }
}
