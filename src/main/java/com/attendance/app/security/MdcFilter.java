package com.attendance.app.security;

import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

/**
 * MdcFilter
 *
 * リクエスト毎に一意の traceId を発行し、さらにログイン済みユーザーの userId を
 * MDC (Mapped Diagnostic Context) に設定するサーブレットフィルタ。
 * 複数スレッドのログが入り乱れても、同一リクエストのログを容易に抽出可能になります。
 */
public class MdcFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_KEY = "traceId";
    private static final String USER_ID_KEY = "userId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        try {
            // traceIdがMDCにない場合は生成してセット
            if (MDC.get(TRACE_ID_KEY) == null) {
                MDC.put(TRACE_ID_KEY, UUID.randomUUID().toString());
            }

            // Authenticationからユーザー情報を取得してuserIdをセット (もしあれば)
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated() && !authentication.getName().equals("anonymousUser")) {
                MDC.put(USER_ID_KEY, authentication.getName());
            } else {
                MDC.put(USER_ID_KEY, "anonymous");
            }

            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID_KEY);
            MDC.remove(USER_ID_KEY);
            MDC.clear();
        }
    }
}
