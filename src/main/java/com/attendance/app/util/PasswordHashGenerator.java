package com.attendance.app.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * パスワードハッシュ生成ユーティリティ。
 * マイグレーションで使用するハッシュを生成するための一時的なクラスです。
 */
public class PasswordHashGenerator {
    /**
     * パスワードハッシュを生成・検証するメインメソッド。
     *
     * @param args コマンドライン引数
     */
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);
        
        // 各ユーザーのパスワード
        String adminPassword = "admin123";
        String userPassword = "user123";
        String testPassword = "test123";
        
        System.out.println("=== Password Hash Generation ===");
        System.out.println();
        System.out.println("Admin (admin@example.com) - Password: " + adminPassword);
        System.out.println("Hash: " + encoder.encode(adminPassword));
        System.out.println();
        System.out.println("User (user@example.com) - Password: " + userPassword);
        System.out.println("Hash: " + encoder.encode(userPassword));
        System.out.println();
        System.out.println("Test (test@example.com) - Password: " + testPassword);
        System.out.println("Hash: " + encoder.encode(testPassword));
        System.out.println();
        
        // 検証テスト（既存のハッシュが正しいかどうか）
        System.out.println("=== Verification Test ===");
        String existingHash1 = "$2a$10$nc0xCK6paqfGGJv1PpjVn.oHq.6cIj5LF/wqtLczR.uaGLBA7uP7y";
        String existingHash2 = "$2a$10$XsPBT0WsznxOIgxs.BbDl.jHwSiGkORFPU1ubt2Wt2deyr6OgkyEy";
        String existingHash3 = "$2a$10$aKc3Tyf35GTprhJg/fX.q.3k.xIadYyti4EjxKx45NO8rWfwWfOa.";
        
        System.out.println("Hash 1 matches 'admin123': " + encoder.matches(adminPassword, existingHash1));
        System.out.println("Hash 2 matches 'user123': " + encoder.matches(userPassword, existingHash2));
        System.out.println("Hash 3 matches 'test123': " + encoder.matches(testPassword, existingHash3));
    }
}
