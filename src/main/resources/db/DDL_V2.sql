CREATE TABLE member (
    member_id BIGINT NOT NULL AUTO_INCREMENT,
    social_provider VARCHAR(20) NOT NULL,
    social_subject VARCHAR(255) NOT NULL,
    social_email VARCHAR(255) NULL,
    name VARCHAR(10) NOT NULL,
    phone_number CHAR(11) NOT NULL,
    role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,

    PRIMARY KEY (member_id),

    CONSTRAINT uk_member_social_provider_subject
        UNIQUE (social_provider, social_subject)

)ENGINE=InnoDB
 DEFAULT CHARSET = utf8mb4
 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE work_place (
    work_place_id BIGINT NOT NULL AUTO_INCREMENT,
    owner_member_id BIGINT NOT NULL,
    size VARCHAR(30) NOT NULL,
    name VARCHAR(100) NOT NULL,
    road_address VARCHAR(255) NOT NULL,
    detail_address VARCHAR(100) NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,

    PRIMARY KEY (work_place_id)

)ENGINE=InnoDB
 DEFAULT CHARSET = utf8mb4
 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_work_place_owner_member_id
    ON work_place (owner_member_id);


CREATE TABLE crew (
    crew_id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    work_place_id BIGINT NOT NULL,
    join_status VARCHAR(20) NOT NULL,
    crew_role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    deleted_at DATETIME NULL,

    PRIMARY KEY (crew_id),

    CONSTRAINT fk_crew_member
        FOREIGN KEY (member_id)
            REFERENCES member (member_id),

    CONSTRAINT fk_crew_work_place
        FOREIGN KEY (work_place_id)
            REFERENCES work_place (work_place_id),

    CONSTRAINT uk_crew_member_work_place
        UNIQUE (member_id, work_place_id)

)ENGINE=InnoDB
 DEFAULT CHARSET = utf8mb4
 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE crew_invitation (
    crew_invitation_id BIGINT NOT NULL AUTO_INCREMENT,
    work_place_id BIGINT NOT NULL,
    created_by_member_id BIGINT NOT NULL,
    invite_code CHAR(6) NOT NULL,
    status VARCHAR(20) NOT NULL,
    expires_at DATETIME NOT NULL,
    used_at DATETIME NULL,
    used_by_member_id BIGINT NULL,
    failed_attempt_count INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,

    PRIMARY KEY (crew_invitation_id),

    CONSTRAINT uk_crew_invitation_invite_code
        UNIQUE (invite_code),

    CONSTRAINT fk_crew_invitation_work_place
        FOREIGN KEY (work_place_id)
            REFERENCES work_place (work_place_id)

)ENGINE=InnoDB
 DEFAULT CHARSET = utf8mb4
 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_crew_invitation_work_place_status
    ON crew_invitation (work_place_id, status);

CREATE INDEX idx_crew_invitation_invite_code_status
    ON crew_invitation (invite_code, status);

CREATE INDEX idx_crew_invitation_expires_at_status
    ON crew_invitation (expires_at, status);


CREATE TABLE terms (
    terms_id BIGINT NOT NULL AUTO_INCREMENT,
    terms_type VARCHAR(50) NOT NULL,
    title VARCHAR(100) NOT NULL,
    required TINYINT(1) NOT NULL,
    status VARCHAR(20) NOT NULL,
    version VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (terms_id),

    CONSTRAINT chk_terms_required
        CHECK (required IN (0, 1))

)ENGINE=InnoDB
 DEFAULT CHARSET = utf8mb4
 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_terms_type_status
    ON terms (terms_type, status);

CREATE INDEX idx_terms_status_required
    ON terms (status, required);



CREATE TABLE member_terms_agreement (
    member_terms_agreement_id BIGINT NOT NULL AUTO_INCREMENT,
    terms_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    agreed TINYINT(1) NOT NULL,
    agreed_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (member_terms_agreement_id),

    CONSTRAINT fk_member_terms_agreement_terms
        FOREIGN KEY (terms_id)
            REFERENCES terms (terms_id),

    CONSTRAINT uk_member_terms_agreement_member_terms
        UNIQUE (member_id, terms_id),

    CONSTRAINT chk_member_terms_agreement_agreed
        CHECK (agreed IN (0, 1))

)ENGINE=InnoDB
 DEFAULT CHARSET = utf8mb4
 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_member_terms_agreement_member_id
    ON member_terms_agreement (member_id);
