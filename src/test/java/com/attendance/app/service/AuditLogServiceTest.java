package com.attendance.app.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.attendance.app.entity.AuditEventType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AuditLogService} の単体テスト。
 *
 * <p>各 record メソッドが監査専用 logger へ正しいCSV形式メッセージを出力することを検証する。
 */
@DisplayName("AuditLogService")
class AuditLogServiceTest {

    private static final String AUDIT_LOGGER_NAME = "com.attendance.app.audit";

    private final AuditLogService auditLogService = new AuditLogService();

    private Logger auditLogger;
    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        auditLogger = (Logger) LoggerFactory.getLogger(AUDIT_LOGGER_NAME);
        auditLogger.setLevel(Level.INFO);

        listAppender = new ListAppender<>();
        listAppender.start();
        auditLogger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        if (auditLogger != null && listAppender != null) {
            auditLogger.detachAppender(listAppender);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // recordSubmissionEvent
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("recordSubmissionEvent")
    class RecordSubmissionEvent {

        @Test
        @DisplayName("SUBMISSION_APPROVED が専用 logger に出力される")
        void approved_buildsCorrectMessage() {
            auditLogService.recordSubmissionEvent(
                    AuditEventType.SUBMISSION_APPROVED, 10L, 20L, 99L, "対象月: 2026-04");

            assertThat(listAppender.list).hasSize(1);
            assertThat(listAppender.list.get(0).getFormattedMessage())
                .startsWith("\"SUBMISSION_APPROVED\",\"10\",\"20\",\"ATTENDANCE_SUBMISSION\",\"99\",\"対象月: 2026-04\",\"")
                .endsWith("\"");
        }

        @Test
        @DisplayName("description が null でも空文字として出力される")
        void nullDescription_isConvertedToEmptyField() {
            auditLogService.recordSubmissionEvent(
                AuditEventType.SUBMISSION_WITHDRAWN, 5L, 5L, 1L, null);

            assertThat(listAppender.list).hasSize(1);
            assertThat(listAppender.list.get(0).getFormattedMessage())
                .startsWith("\"SUBMISSION_WITHDRAWN\",\"5\",\"5\",\"ATTENDANCE_SUBMISSION\",\"1\",\"\",\"")
                .endsWith("\"");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // recordUserEvent
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("recordUserEvent")
    class RecordUserEvent {

        @Test
        @DisplayName("USER_CREATED が専用 logger に出力される")
        void created_buildsCorrectMessage() {
            auditLogService.recordUserEvent(
                    AuditEventType.USER_CREATED, 1L, 42L, "ロール: USER");

            assertThat(listAppender.list).hasSize(1);
            assertThat(listAppender.list.get(0).getFormattedMessage())
                    .startsWith("\"USER_CREATED\",\"1\",\"42\",\"USER\",\"42\",\"ロール: USER\",\"")
                    .endsWith("\"");
        }

        @Test
        @DisplayName("USER_DELETED で description が null の場合も出力できる")
        void deleted_nullDescription_accepted() {
            auditLogService.recordUserEvent(
                    AuditEventType.USER_DELETED, 1L, 99L, null);

            assertThat(listAppender.list).hasSize(1);
            assertThat(listAppender.list.get(0).getFormattedMessage())
                    .startsWith("\"USER_DELETED\",\"1\",\"99\",\"USER\",\"99\",\"\",\"")
                    .endsWith("\"");
        }
    }
}
