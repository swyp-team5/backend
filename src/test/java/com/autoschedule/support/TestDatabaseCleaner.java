package com.autoschedule.support;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 통합 테스트에서 모든 도메인 테이블을 외래키 참조 역순으로 정리한다.
 */
public final class TestDatabaseCleaner {

    private static final String[] TABLES = {
            "time_detail",
            "day",
            "week_schedule",
            "notification_delivery",
            "notification",
            "fcm_token",
            "notice_comment",
            "notice",
            "member_terms_agreement",
            "terms",
            "crew_invitation",
            "crew",
            "profile_image",
            "work_place",
            "member_notification_setting",
            "member"
    };

    private TestDatabaseCleaner() {
    }

    /**
     * 현재 테스트 스키마에 존재하는 테이블만 삭제한다.
     */
    public static void clean(JdbcTemplate jdbcTemplate) {
        for (String table : TABLES) {
            if (tableExists(jdbcTemplate, table)) {
                jdbcTemplate.update("delete from " + table);
            }
        }
    }

    /**
     * 테스트 컨텍스트에 아직 생성되지 않은 테이블 삭제 시도를 방지한다.
     */
    private static boolean tableExists(JdbcTemplate jdbcTemplate, String tableName) {
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
