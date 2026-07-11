package com.attendance.app.service;

import com.attendance.app.entity.User;
import com.attendance.app.mapper.SystemSettingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CSV 出力ファイル名パターンの取得・検証・展開を扱うサービスです。
 */
@Service
@RequiredArgsConstructor
public class CsvFilenamePatternService {

    public static final String SETTING_KEY = "csv_filename_pattern";
    public static final String DEFAULT_PATTERN = "{yyyy}-{MM}_{userId}_{name}_{downloadAt}";

    private static final DateTimeFormatter DOWNLOAD_AT_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\{[^{}]+\\}");
    private static final Set<String> ALLOWED_TOKENS = Set.of(
            "{yyyy}",
            "{MM}",
            "{userId}",
            "{name}",
            "{downloadAt}");

    private final SystemSettingMapper systemSettingMapper;

    /**
     * 現在のファイル名パターンを取得します。
     */
    public String getPattern() {
        String pattern = systemSettingMapper.selectValueByKey(SETTING_KEY);
        if (pattern == null || pattern.trim().isEmpty()) {
            return DEFAULT_PATTERN;
        }
        return pattern.trim();
    }

    /**
     * ファイル名パターンを検証した上で保存します。
     */
    public void updatePattern(String pattern) {
        String normalized = pattern == null ? "" : pattern.trim();
        validatePattern(normalized);
        systemSettingMapper.upsertValue(SETTING_KEY, normalized);
    }

    /**
     * 指定ユーザー・対象月・出力時刻から CSV ファイル名を生成します。
     */
    public String buildCsvFilename(User user, YearMonth yearMonth, OffsetDateTime downloadedAt) {
        String pattern = getPattern();
        String userId = user != null && user.getUserId() != null
                ? String.format("%03d", user.getUserId())
                : "000";
        String resolved = pattern
                .replace("{yyyy}", String.format("%04d", yearMonth.getYear()))
                .replace("{MM}", String.format("%02d", yearMonth.getMonthValue()))
                .replace("{userId}", userId)
                .replace("{name}", sanitizeFilename(user != null ? user.getFullName() : null))
                .replace("{downloadAt}", downloadedAt.format(DOWNLOAD_AT_FORMATTER));

        String filename = sanitizeFilename(resolved);
        if (!filename.toLowerCase().endsWith(".csv")) {
            filename += ".csv";
        }
        return filename;
    }

    private void validatePattern(String pattern) {
        if (pattern.isEmpty()) {
            throw new IllegalArgumentException("ファイル名パターンを入力してください");
        }
        if (pattern.length() > 200) {
            throw new IllegalArgumentException("ファイル名パターンは200文字以内で入力してください");
        }
        if (!hasBalancedBraces(pattern)) {
            throw new IllegalArgumentException("ファイル名パターンの波括弧の対応が不正です");
        }

        Matcher matcher = TOKEN_PATTERN.matcher(pattern);
        while (matcher.find()) {
            String token = matcher.group();
            if (!ALLOWED_TOKENS.contains(token)) {
                throw new IllegalArgumentException("ファイル名パターンに未対応トークンがあります: " + token);
            }
        }

        requireToken(pattern, "{yyyy}");
        requireToken(pattern, "{MM}");
        requireToken(pattern, "{userId}");
        requireToken(pattern, "{name}");
        requireToken(pattern, "{downloadAt}");
    }

    private void requireToken(String pattern, String token) {
        if (!pattern.contains(token)) {
            throw new IllegalArgumentException("ファイル名パターンには " + token + " を含めてください");
        }
    }

    private boolean hasBalancedBraces(String pattern) {
        int depth = 0;
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth < 0) {
                    return false;
                }
            }
        }
        return depth == 0;
    }

    private String sanitizeFilename(String name) {
        if (name == null) {
            return "unknown";
        }
        return name.replaceAll("[\\\\/:*?\"<>|\\s]", "_");
    }
}
