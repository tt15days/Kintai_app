package com.attendance.app.util;

/**
 * CSV出力時のフォーミュラインジェクション対策ユーティリティ。
 */
public final class CsvSanitizeUtil {

    private CsvSanitizeUtil() {
    }

    /**
     * セル値が数式として解釈されうる文字（=, +, -, @, タブ, CR）で始まる場合、
     * 先頭にシングルクォートを付与して無害化します。
     * Excel/LibreOffice等でCSVを開いた際の数式実行を防ぎます。
     *
     * @param value 対象文字列
     * @return 無害化後の文字列（null または空文字はそのまま返す）
     */
    public static String sanitizeFormulaInjection(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        char first = value.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
            return "'" + value;
        }
        return value;
    }
}
