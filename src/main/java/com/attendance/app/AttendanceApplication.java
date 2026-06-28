package com.attendance.app;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

/**
 * 勤怠管理アプリケーションのメインクラス。
 * Spring Boot アプリケーションの起動ポイントとなります。
 */
@SpringBootApplication
@EnableScheduling
@MapperScan("com.attendance.app.mapper")
public class AttendanceApplication {

    /**
     * アプリケーションのエントリーポイント。
     *
     * @param args コマンドライン引数
     */
    public static void main(String[] args) {
        // JVM のデフォルトタイムゾーンを JST に固定する。
        // これにより Thymeleaf の #temporals.format(Instant, pattern) が
        // ZoneId.systemDefault() = Asia/Tokyo で書式化されるため、
        // attendance_date / start_time / end_time の日付ずれを防ぐ。
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
        SpringApplication.run(AttendanceApplication.class, args);
    }
}
