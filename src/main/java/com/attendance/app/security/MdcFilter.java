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
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

/**
 * MdcFilter
 *
 * リクエスト毎に一意の traceId を発行し、さらにログイン済みユーザーの userId を
 * MDC (Mapped Diagnostic Context) に設定するサーブレットフィルタ。
 * 複数スレッドのログが入り乱れても、同一リクエストのログを容易に抽出可能になります。
 */
@Slf4j
public class MdcFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_KEY = "traceId";
    private static final String USER_ID_KEY = "userId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        long startTime = System.currentTimeMillis();
        try {
            // traceIdがMDCにない場合は生成してセット
            if (MDC.get(TRACE_ID_KEY) == null) {
                MDC.put(TRACE_ID_KEY, UUID.randomUUID().toString());
            }

            // Authenticationからユーザー情報を取得してuserIdをセット (もしあれば)
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()
                    && !authentication.getName().equals("anonymousUser")) {
                MDC.put(USER_ID_KEY, authentication.getName());
            } else {
                MDC.put(USER_ID_KEY, "anonymous");
            }

            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            int status = response.getStatus();
            logRequest(request, status, duration);

            MDC.remove(TRACE_ID_KEY);
            MDC.remove(USER_ID_KEY);
            MDC.clear();
        }
    }

    private void logRequest(HttpServletRequest request, int status, long duration) {
        String method = request.getMethod();
        String uri = request.getRequestURI();

        // 静的リソースへのリクエストはログ出力から除外する
        if (isStaticResource(uri)) {
            return;
        }

        String queryString = request.getQueryString();
        String relativePath = (queryString != null) ? uri + "?" + queryString : uri;

        if ("GET".equalsIgnoreCase(method)) {
            log.info("リクエスト=[GET], パス={}, ステータス={}, 処理時間={}ms", relativePath, status, duration);
        } else if ("POST".equalsIgnoreCase(method)) {
            String postData = getPostData(request);
            log.info("リクエスト=[POST], パス={}, 送信内容=[{}], ステータス={}, 処理時間={}ms", relativePath, postData, status,
                    duration);
        }
    }

    private boolean isStaticResource(String uri) {
        if (uri == null)
            return false;
        return uri.startsWith("/css/") ||
                uri.startsWith("/js/") ||
                uri.startsWith("/images/") ||
                uri.equals("/tailwind.css") ||
                uri.equals("/favicon.ico") ||
                uri.endsWith(".css") ||
                uri.endsWith(".js") ||
                uri.endsWith(".png") ||
                uri.endsWith(".jpg") ||
                uri.endsWith(".jpeg") ||
                uri.endsWith(".gif") ||
                uri.endsWith(".ico") ||
                uri.endsWith(".svg") ||
                uri.endsWith(".woff") ||
                uri.endsWith(".woff2") ||
                uri.endsWith(".ttf");
    }

    private String getPostData(HttpServletRequest request) {
        Map<String, String[]> params = request.getParameterMap();
        if (params == null || params.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        params.forEach((key, values) -> {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(key).append("=");
            if (isSensitiveField(key)) {
                sb.append("****");
            } else {
                if (values != null) {
                    if (values.length == 1) {
                        sb.append(values[0]);
                    } else {
                        sb.append(Arrays.toString(values));
                    }
                } else {
                    sb.append("null");
                }
            }
        });
        return sb.toString();
    }

    private boolean isSensitiveField(String key) {
        if (key == null)
            return false;
        String lower = key.toLowerCase();
        return lower.contains("password") ||
                lower.contains("passwd") ||
                lower.contains("secret") ||
                lower.contains("_csrf") ||
                lower.contains("token") ||
                lower.contains("email") ||
                lower.contains("phone") ||
                lower.contains("tel") ||
                lower.contains("address") ||
                lower.contains("reason") ||
                lower.contains("comment") ||
                lower.contains("notes");
    }
}
