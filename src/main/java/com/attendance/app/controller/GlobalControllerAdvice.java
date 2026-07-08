package com.attendance.app.controller;

import com.attendance.app.entity.User;
import com.attendance.app.mapper.SystemSettingMapper;
import com.attendance.app.security.SecurityUtil;
import com.attendance.app.service.UserService;
import com.attendance.app.service.AttendanceSubmissionService;
import com.attendance.app.service.AttendanceCorrectionRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Global Controller Advice - 全コントローラー共通のデータ処理
 *
 * 全ての画面表示用モデルに対して、ログイン中のユーザー情報を自動的に追加します。
 * これにより、サイドバーなどの共通コンポーネントでユーザー情報を安全に表示できます。
 */
@Slf4j
@ControllerAdvice
@RequiredArgsConstructor
public class GlobalControllerAdvice {

    private final SecurityUtil securityUtil;
    private final SystemSettingMapper systemSettingMapper;
    private final UserService userService;
    private final AttendanceSubmissionService attendanceSubmissionService;
    private final AttendanceCorrectionRequestService correctionRequestService;

    /**
     * ログイン中のユーザー情報をモデルに追加します。
     *
     * @param model Spring MVC モデル
     */
    @ModelAttribute
    public void addCurrentUser(Model model) {
        try {
            if (securityUtil.isAuthenticated() && securityUtil.getCurrentUsername().isPresent()) {
                String username = securityUtil.getCurrentUsername().get();
                if (!"anonymousUser".equals(username)) {
                    User currentUser = securityUtil.getCurrentUser();
                    model.addAttribute("currentUser", currentUser);
                }
            }
        } catch (Exception e) {
            // 未ログイン時または匿名ユーザーアクセス時は例外を無視します
            log.trace("グローバルユーザー情報の取得をスキップしました: {}", e.getMessage());
        }
    }

    /**
     * 承認権限を持つユーザーに対して、承認待ち件数をモデルに追加します。
     *
     * @param model Spring MVC モデル
     */
    @ModelAttribute
    public void addPendingApprovalsCount(Model model) {
        try {
            if (securityUtil.isAuthenticated() && securityUtil.getCurrentUsername().isPresent()) {
                String username = securityUtil.getCurrentUsername().get();
                if (!"anonymousUser".equals(username)) {
                    User currentUser = securityUtil.getCurrentUser();
                    if (currentUser != null && userService.isAttendanceApprover(currentUser)) {
                        int pendingSubmissionsCount = attendanceSubmissionService.getPendingSubmissions(currentUser).size();
                        int pendingCorrectionsCount = correctionRequestService.getPendingRequests(currentUser).size();
                        model.addAttribute("pendingSubmissionsCount", pendingSubmissionsCount);
                        model.addAttribute("pendingCorrectionsCount", pendingCorrectionsCount);
                    }
                }
            }
        } catch (Exception e) {
            log.trace("グローバル承認待ち件数の取得をスキップしました: {}", e.getMessage());
        }
    }

    /**
     * 共通のコピーライト表示文言をモデルに追加します。
     *
     * @param model Spring MVC モデル
     */
    @ModelAttribute
    public void addCopyrightText(Model model) {
        try {
            String copyright = systemSettingMapper.selectValueByKey("COPYRIGHT_TEXT");
            if (copyright == null || copyright.trim().isEmpty()) {
                copyright = "© 2026 勤怠管理システム";
            }
            model.addAttribute("copyrightText", copyright);
        } catch (Exception e) {
            log.warn("コピーライト設定の取得に失敗しました。デフォルト値を使用します。: {}", e.getMessage());
            model.addAttribute("copyrightText", "© 2026 勤怠管理システム");
        }
    }

    /**
     * 共通のシステム名表示文言をモデルに追加します。
     *
     * @param model Spring MVC モデル
     */
    @ModelAttribute
    public void addSystemName(Model model) {
        try {
            String systemName = systemSettingMapper.selectValueByKey("SYSTEM_NAME");
            if (systemName == null || systemName.trim().isEmpty()) {
                systemName = "勤怠管理システム";
            }
            model.addAttribute("systemName", systemName);
        } catch (Exception e) {
            log.warn("システム名設定の取得に失敗しました。デフォルト値を使用します。: {}", e.getMessage());
            model.addAttribute("systemName", "勤怠管理システム");
        }
    }

    /**
     * 社員番号プレフィックス設定をモデルに追加します。
     *
     * @param model Spring MVC モデル
     */
    @ModelAttribute
    public void addEmpNoPrefix(Model model) {
        try {
            String prefix = systemSettingMapper.selectValueByKey("EMP_NO_PREFIX");
            if (prefix == null) {
                prefix = "";
            }
            model.addAttribute("empNoPrefix", prefix);
        } catch (Exception e) {
            log.warn("社員番号プレフィックス設定の取得に失敗しました。デフォルト値（空）を使用します。: {}", e.getMessage());
            model.addAttribute("empNoPrefix", "");
        }
    }
}

