package com.autoschedule.global.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 전역 예외 처리기가 표준 에러 응답을 만드는지 검증한다.
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    /**
     * 테스트 컨트롤러와 전역 예외 처리기를 독립 MockMvc 환경으로 구성한다.
     */
    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(JsonMapper.builder()
                        .findAndAddModules()
                        .build()))
                .build();
    }

    /**
     * 서비스 API 예외가 지정한 HTTP 상태와 에러 코드로 응답되는지 검증한다.
     */
    @Test
    void handlesApiException() throws Exception {
        mockMvc.perform(get("/test/api-exception"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("4005"))
                .andExpect(jsonPath("$.message").value("conflict happened"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.path").value("/test/api-exception"));
    }

    /**
     * 요청 본문 DTO 검증 실패가 필드 오류를 포함한 400 응답으로 변환되는지 검증한다.
     */
    @Test
    void handlesValidationException() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("4000"))
                .andExpect(jsonPath("$.message").value("요청 값이 올바르지 않습니다."))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    /**
     * 검증 메시지가 비어 있을 때 한글 기본 메시지를 내려주는지 검증한다.
     */
    @Test
    void handlesValidationExceptionWithKoreanFallbackMessage() throws Exception {
        mockMvc.perform(post("/test/validation-empty-message")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").value("올바르지 않은 값입니다."));
    }

    /**
     * 요청 파라미터 검증 실패가 필드 오류를 포함한 400 응답으로 변환되는지 검증한다.
     */
    @Test
    void handlesMethodParameterValidationExceptionWithFieldErrors() throws Exception {
        mockMvc.perform(get("/test/parameter-validation")
                        .param("name", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("4000"))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").value("이름은 필수입니다."));
    }

    /**
     * 전역 예외 처리 테스트에 필요한 예외와 검증 실패를 발생시키는 테스트용 컨트롤러다.
     */
    @RestController
    @RequestMapping("/test")
    static class TestController {

        /**
         * API 예외 처리 경로를 검증하기 위해 충돌 예외를 발생시킨다.
         */
        @GetMapping("/api-exception")
        void throwApiException() {
            throw new ApiException(ErrorCode.CONFLICT, "conflict happened");
        }

        /**
         * 요청 본문 DTO 검증 실패 경로를 검증한다.
         */
        @PostMapping("/validation")
        void validate(@Valid @RequestBody TestRequest request) {
        }

        /**
         * 빈 검증 메시지의 기본 메시지 치환 경로를 검증한다.
         */
        @PostMapping("/validation-empty-message")
        void validateEmptyMessage(@Valid @RequestBody EmptyMessageRequest request) {
        }

        /**
         * 메서드 파라미터 검증 실패 경로를 검증한다.
         */
        @GetMapping("/parameter-validation")
        void validateParameter(@RequestParam("name") @NotBlank(message = "이름은 필수입니다.") String name) {
        }
    }

    /**
     * 기본 Bean Validation 메시지를 사용하는 테스트 요청 DTO다.
     */
    record TestRequest(
            @NotBlank String name
    ) {
    }

    /**
     * 빈 검증 메시지 fallback을 확인하기 위한 테스트 요청 DTO다.
     */
    record EmptyMessageRequest(
            @NotBlank(message = "") String name
    ) {
    }
}
