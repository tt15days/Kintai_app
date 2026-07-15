package com.attendance.app.security;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MdcFilterTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    void authenticatedRequest_doesNotPutPrincipalNameInMdc() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user@example.com", "password", List.of()));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/dashboard");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> userContext = new AtomicReference<>();

        new MdcFilter().doFilter(request, response,
                (servletRequest, servletResponse) -> userContext.set(MDC.get("userId")));

        assertThat(userContext.get()).isEqualTo("authenticated");
        assertThat(MDC.get("userId")).isNull();
    }

    @Test
    void requestLog_doesNotContainQueryOrFormValues() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(MdcFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/attendance/saveAll");
            request.setQueryString("yearMonth=secret-query");
            request.addParameter("remarks", "secret-form-value");

            new MdcFilter().doFilter(request, new MockHttpServletResponse(),
                    (servletRequest, servletResponse) -> { });

            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.get(0).getFormattedMessage())
                    .contains("リクエスト=[POST]", "パス=/attendance/saveAll")
                    .doesNotContain("secret-query", "secret-form-value", "remarks");
        } finally {
            logger.detachAppender(appender);
        }
    }
}
