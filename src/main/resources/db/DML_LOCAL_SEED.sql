SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

INSERT INTO terms (
    terms_id,
    terms_type,
    title,
    required,
    status,
    version,
    content
) VALUES
    (
        1,
        'COMMON',
        '만 14세 이상 확인',
        1,
        'ACTIVE',
        '1.0',
        '로컬 테스트용 약관입니다. 실제 서비스 약관 확정 전까지 프론트엔드 API 연동 검증에만 사용합니다.'
    ),
    (
        2,
        'COMMON',
        '서비스 이용약관',
        1,
        'ACTIVE',
        '1.0',
        '로컬 테스트용 서비스 이용약관입니다. 실제 운영 약관 내용이 아닙니다.'
    ),
    (
        3,
        'COMMON',
        '개인정보 수집 및 이용 동의',
        1,
        'ACTIVE',
        '1.0',
        '로컬 테스트용 개인정보 수집 및 이용 동의입니다. 실제 운영 약관 내용이 아닙니다.'
    ),
    (
        4,
        'COMMON',
        '마케팅 정보 수신 동의',
        0,
        'ACTIVE',
        '1.0',
        '로컬 테스트용 선택 약관입니다. 실제 운영 약관 내용이 아닙니다.'
    ),
    (
        5,
        'OWNER',
        '개인정보보호의무',
        1,
        'ACTIVE',
        '1.0',
        '로컬 테스트용 사장님 추가 약관입니다. 실제 운영 약관 내용이 아닙니다.'
    )
ON DUPLICATE KEY UPDATE
    terms_type = VALUES(terms_type),
    title = VALUES(title),
    required = VALUES(required),
    status = VALUES(status),
    version = VALUES(version),
    content = VALUES(content);
