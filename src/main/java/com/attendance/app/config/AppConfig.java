package com.attendance.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.ZoneId;

/**
 * アプリケーション設定クラス。
 * Spring WebMVC や MyBatis、Jackson などの設定を行います。
 */
@Configuration
@EnableScheduling
public class AppConfig implements WebMvcConfigurer {

    /**
     * 日本のタイムゾーン定義
     */
    public static final ZoneId JAPAN_ZONE = ZoneId.of("Asia/Tokyo");

    /**
     * 日本のタイムゾーン文字列の Bean 定義。
     *
     * @return タイムゾーン文字列 ("Asia/Tokyo")
     */
    @Bean
    public String japanTimeZone() {
        return "Asia/Tokyo";
    }

    /**
     * JSON 変換用の ObjectMapper を設定します。
     * Java 8 の日時型（LocalDate, OffsetDateTime 等）をサポートするため、
     * jackson-datatype-jsr310 モジュールを登録します。
     *
     * @return 設定済みの ObjectMapper オブジェクト
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // java.time.*型のサポートを有効化
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

}
