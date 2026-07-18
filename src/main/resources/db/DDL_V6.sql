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

CREATE TABLE member_notification_setting (
    member_notification_setting_id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    fcm_push_enabled BIT(1) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (member_notification_setting_id),

    CONSTRAINT fk_member_notification_setting_member
        FOREIGN KEY (member_id)
            REFERENCES member (member_id),

    CONSTRAINT uk_member_notification_setting_member
        UNIQUE (member_id),

    CONSTRAINT chk_member_notification_setting_fcm_push_enabled
        CHECK (fcm_push_enabled IN (0, 1))

)ENGINE=InnoDB
 DEFAULT CHARSET = utf8mb4
 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_member_notification_setting_member_id
    ON member_notification_setting (member_id);

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

CREATE TABLE profile_image (
    profile_image_id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    stored_file_name VARCHAR(100) NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    image_url VARCHAR(700) NOT NULL,
    content_type VARCHAR(50) NOT NULL,
    file_size BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    uploaded_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    active_member_id BIGINT
        GENERATED ALWAYS AS (
            CASE
                WHEN status = 'ACTIVE' AND deleted_at IS NULL THEN member_id
                ELSE NULL
            END
        ) STORED,
    pending_member_id BIGINT
        GENERATED ALWAYS AS (
            CASE
                WHEN status = 'PENDING' AND deleted_at IS NULL THEN member_id
                ELSE NULL
            END
        ) STORED,

    PRIMARY KEY (profile_image_id),

    CONSTRAINT fk_profile_image_member
        FOREIGN KEY (member_id)
            REFERENCES member (member_id),

    CONSTRAINT uk_profile_image_object_key
        UNIQUE (object_key),

    CONSTRAINT uk_profile_image_active_member
        UNIQUE (active_member_id),

    CONSTRAINT uk_profile_image_pending_member
        UNIQUE (pending_member_id)

)ENGINE=InnoDB
 DEFAULT CHARSET = utf8mb4
 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_profile_image_member_status_deleted
    ON profile_image (member_id, status, deleted_at);


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

CREATE INDEX idx_crew_member_join_status_status_deleted_work_place
    ON crew (member_id, join_status, status, deleted_at, work_place_id);

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

    CONSTRAINT uk_terms_type_title_version
        UNIQUE (terms_type, title, version),

    CONSTRAINT chk_terms_required
        CHECK (required IN (0, 1))

)ENGINE=InnoDB
 DEFAULT CHARSET = utf8mb4
 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_terms_type_status
    ON terms (terms_type, status);



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

CREATE INDEX idx_member_terms_agreement_terms_id
    ON member_terms_agreement (terms_id);


CREATE TABLE notice (
    notice_id BIGINT NOT NULL AUTO_INCREMENT,
    work_place_id BIGINT NOT NULL,
    writer_member_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    content TEXT NOT NULL,
    representative TINYINT(1) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,

    PRIMARY KEY (notice_id),

    CONSTRAINT fk_notice_work_place
        FOREIGN KEY (work_place_id)
            REFERENCES work_place (work_place_id),

    CONSTRAINT chk_notice_representative
        CHECK (representative IN (0, 1))

)ENGINE=InnoDB
 DEFAULT CHARSET = utf8mb4
 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_notice_work_place_status_deleted_created_id
    ON notice (work_place_id, status, deleted_at, created_at DESC, notice_id DESC);

CREATE INDEX idx_notice_work_place_representative_status_deleted
    ON notice (work_place_id, representative, status, deleted_at);

CREATE TABLE notice_image (
    notice_image_id BIGINT NOT NULL AUTO_INCREMENT,
    notice_id BIGINT NULL,
    uploader_member_id BIGINT NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    stored_file_name VARCHAR(100) NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    image_url VARCHAR(700) NOT NULL,
    content_type VARCHAR(50) NOT NULL,
    file_size BIGINT NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    uploaded_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,

    PRIMARY KEY (notice_image_id),

    CONSTRAINT fk_notice_image_notice
        FOREIGN KEY (notice_id)
            REFERENCES notice (notice_id),

    CONSTRAINT uk_notice_image_object_key
        UNIQUE (object_key),

    CONSTRAINT chk_notice_image_display_order
        CHECK (display_order BETWEEN 0 AND 5)

)ENGINE=InnoDB
 DEFAULT CHARSET = utf8mb4
 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_notice_image_notice_status_deleted_order
    ON notice_image (notice_id, status, deleted_at, display_order, notice_image_id);

CREATE INDEX idx_notice_image_uploader_object_status
    ON notice_image (uploader_member_id, object_key, status, deleted_at);


CREATE TABLE notice_reaction (
    notice_reaction_id BIGINT NOT NULL AUTO_INCREMENT,
    notice_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    reaction_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,

    PRIMARY KEY (notice_reaction_id),

    CONSTRAINT fk_notice_reaction_notice
        FOREIGN KEY (notice_id)
            REFERENCES notice (notice_id),

    CONSTRAINT uk_notice_reaction_notice_member
        UNIQUE (notice_id, member_id)

)ENGINE=InnoDB
 DEFAULT CHARSET = utf8mb4
 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_notice_reaction_notice_status_type
    ON notice_reaction (notice_id, status, reaction_type);

CREATE INDEX idx_notice_reaction_member_status
    ON notice_reaction (member_id, status);


CREATE TABLE notice_comment (
    notice_comment_id BIGINT NOT NULL AUTO_INCREMENT,
    notice_id BIGINT NOT NULL,
    writer_member_id BIGINT NOT NULL,
    content VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,

    PRIMARY KEY (notice_comment_id),

    CONSTRAINT fk_notice_comment_notice
        FOREIGN KEY (notice_id)
            REFERENCES notice (notice_id)

)ENGINE=InnoDB
 DEFAULT CHARSET = utf8mb4
 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_notice_comment_notice_status_deleted_id
    ON notice_comment (notice_id, status, deleted_at, notice_comment_id);


CREATE TABLE fcm_token (
    fcm_token_id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    device_id VARCHAR(100) NOT NULL,
    token VARCHAR(512) NOT NULL,
    platform VARCHAR(20) NOT NULL,
    app_version VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    last_registered_at DATETIME NOT NULL,
    last_used_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,

    PRIMARY KEY (fcm_token_id),

    CONSTRAINT fk_fcm_token_member
        FOREIGN KEY (member_id)
            REFERENCES member (member_id),

    CONSTRAINT uk_fcm_token_member_device
        UNIQUE (member_id, device_id)

)ENGINE=InnoDB
 DEFAULT CHARSET = utf8mb4
 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_fcm_token_member_status
    ON fcm_token (member_id, status);

CREATE INDEX idx_fcm_token_token
    ON fcm_token (token);


CREATE TABLE notification (
    notification_id BIGINT NOT NULL AUTO_INCREMENT,
    receiver_member_id BIGINT NOT NULL,
    notification_type VARCHAR(50) NOT NULL,
    push_policy VARCHAR(20) NOT NULL,
    title VARCHAR(100) NOT NULL,
    body VARCHAR(500) NOT NULL,
    data JSON NULL,
    read_at DATETIME NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,

    PRIMARY KEY (notification_id),

    CONSTRAINT fk_notification_receiver_member
        FOREIGN KEY (receiver_member_id)
            REFERENCES member (member_id)

)ENGINE=InnoDB
 DEFAULT CHARSET = utf8mb4
 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_notification_receiver_status_created
    ON notification (receiver_member_id, status, created_at);

CREATE INDEX idx_notification_receiver_read
    ON notification (receiver_member_id, read_at);


CREATE TABLE notification_delivery (
    notification_delivery_id BIGINT NOT NULL AUTO_INCREMENT,
    notification_id BIGINT NOT NULL,
    fcm_token_id BIGINT NULL,
    channel VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    provider_message_id VARCHAR(255) NULL,
    error_code VARCHAR(100) NULL,
    error_message VARCHAR(500) NULL,
    attempted_at DATETIME NULL,
    sent_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,

    PRIMARY KEY (notification_delivery_id),

    CONSTRAINT fk_notification_delivery_notification
        FOREIGN KEY (notification_id)
            REFERENCES notification (notification_id)

)ENGINE=InnoDB
 DEFAULT CHARSET = utf8mb4
 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_notification_delivery_notification
    ON notification_delivery (notification_id);

CREATE INDEX idx_notification_delivery_status_attempted
    ON notification_delivery (status, attempted_at);

CREATE INDEX idx_notification_delivery_fcm_token
    ON notification_delivery (fcm_token_id);

CREATE TABLE `week_schedule` (
    `week_schedule_id` BIGINT NOT NULL AUTO_INCREMENT,
    `work_place_id` BIGINT NOT NULL,
    `week_schedule_name` VARCHAR(20) NOT NULL,
    `due_date` DATE NOT NULL,
    `work_place_open_time` TIME NOT NULL,
    `work_place_close_time` TIME NOT NULL,
    `min_personal_work_count` INT NOT NULL DEFAULT 1,
    `max_personal_work_count` INT NOT NULL DEFAULT 1,
    `status` VARCHAR(20) NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted_at` DATETIME NULL,

    PRIMARY KEY (`week_schedule_id`),

    CONSTRAINT `fk_week_schedule_work_place`
        FOREIGN KEY (`work_place_id`) REFERENCES `work_place` (`work_place_id`),

    CONSTRAINT `uk_week_schedule_work_place_name`
        UNIQUE (`work_place_id`, `week_schedule_name`)
) ENGINE=InnoDB
DEFAULT CHARSET=utf8mb4
COLLATE=utf8mb4_unicode_ci;

CREATE INDEX `idx_week_schedule_work_place_status_deleted_created`
    ON `week_schedule` (`work_place_id`, `status`, `deleted_at`, `created_at`, `week_schedule_id`);

CREATE TABLE `day` (
    `day_id` BIGINT NOT NULL AUTO_INCREMENT,
    `week_schedule_id` BIGINT NOT NULL,
    `day_name` VARCHAR(20) NOT NULL,
    `date` DATE NOT NULL,
    `grouping_id` INT NULL,
    `work_change_count` INT NOT NULL DEFAULT 0,
    `holiday_status` TINYINT(1) NOT NULL DEFAULT 0,
    `select_limit_status` TINYINT(1) NOT NULL DEFAULT 0,
    `status` VARCHAR(20) NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted_at` DATETIME NULL,

    PRIMARY KEY (`day_id`),

    CONSTRAINT `fk_day_week_schedule`
        FOREIGN KEY (`week_schedule_id`) REFERENCES `week_schedule` (`week_schedule_id`),

    CONSTRAINT `chk_day_holiday_status`
        CHECK (`holiday_status` IN (0, 1)),

    CONSTRAINT `chk_day_select_limit_status`
        CHECK (`select_limit_status` IN (0, 1)),

    CONSTRAINT `uk_day_week_schedule_day_name_date`
        UNIQUE (`week_schedule_id`, `day_name`, `date`)
) ENGINE=InnoDB
DEFAULT CHARSET=utf8mb4
COLLATE=utf8mb4_unicode_ci;

CREATE INDEX `idx_day_week_schedule_status_deleted_date`
    ON `day` (`week_schedule_id`, `status`, `deleted_at`, `date`, `day_id`);

CREATE TABLE `time_detail` (
    `time_detail_id` BIGINT NOT NULL AUTO_INCREMENT,
    `day_id` BIGINT NOT NULL,
    `work_part_no` INT NOT NULL,
    `time_name` VARCHAR(20) NULL,
    `worker_count` INT NOT NULL,
    `start_time` TIME NOT NULL,
    `close_time` TIME NOT NULL,
    `rest_time` INT NOT NULL,
    `status` VARCHAR(20) NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted_at` DATETIME NULL,

    PRIMARY KEY (`time_detail_id`),

    CONSTRAINT `fk_time_detail_day`
        FOREIGN KEY (`day_id`) REFERENCES `day` (`day_id`),

    CONSTRAINT `chk_time_detail_worker_count`
        CHECK (`worker_count` >= 1),

    CONSTRAINT `uk_time_detail_day_work_part_no`
    UNIQUE (`day_id`, `work_part_no`)
) ENGINE=InnoDB
DEFAULT CHARSET=utf8mb4
COLLATE=utf8mb4_unicode_ci;

CREATE INDEX `idx_time_detail_day_status_deleted_work_part`
    ON `time_detail` (`day_id`, `status`, `deleted_at`, `work_part_no`);
