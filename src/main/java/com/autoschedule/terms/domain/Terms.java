package com.autoschedule.terms.domain;

import com.autoschedule.global.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원가입과 서비스 이용에 필요한 약관 원문과 버전을 저장한다.
 */
@Getter
@Entity
@Table(name = "terms")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Terms extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "terms_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "terms_type", nullable = false, length = 50)
    private TermsType termsType;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false)
    private boolean required;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TermsStatus status;

    @Column(nullable = false, length = 20)
    private String version;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * 테스트와 운영 초기 데이터 생성 시 사용할 약관을 만든다.
     */
    public static Terms create(
            TermsType termsType,
            String title,
            boolean required,
            TermsStatus status,
            String content,
            String version
    ) {
        Terms terms = new Terms();
        terms.termsType = termsType;
        terms.title = title;
        terms.required = required;
        terms.status = status;
        terms.content = content;
        terms.version = version;
        return terms;
    }

}
