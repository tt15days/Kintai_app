package com.attendance.app.controller;

import com.attendance.app.entity.AdminAnnouncement;
import com.attendance.app.security.SecurityUtil;
import com.attendance.app.service.AdminAnnouncementService;
import com.attendance.app.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * AdminAnnouncementController - 管理者お知らせ管理
 *
 * 主な責務:
 * - お知らせ一覧表示
 * - お知らせの登録・編集・削除
 */
@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/announcements")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAnnouncementController {

    private static final String ANNOUNCEMENTS_VIEW = "admin/announcements";
    private static final String ANNOUNCEMENTS_REDIRECT = "redirect:/admin/announcements";

    private final AdminAnnouncementService adminAnnouncementService;
    private final SecurityUtil securityUtil;

    /**
     * お知らせ一覧画面を表示します。
     *
     * @param model Spring MVC のモデル
     * @return テンプレート名 (admin/announcements)
     */
    @GetMapping
    public String listAnnouncements(Model model) {
        model.addAttribute("announcements", adminAnnouncementService.getAllAnnouncements());
        model.addAttribute("newAnnouncement", new AdminAnnouncement());
        model.addAttribute("today", DateTimeUtil.todayJapan());
        return ANNOUNCEMENTS_VIEW;
    }

    /**
     * お知らせを登録します。
     *
     * @param title            タイトル
     * @param message          本文
     * @param displayStartDate 表示開始日
     * @param displayEndDate   表示終了日（任意）
     * @param redirectAttributes リダイレクト属性
     * @return リダイレクト先
     */
    @PostMapping("/create")
    public String createAnnouncement(
            @RequestParam String title,
            @RequestParam String message,
            @RequestParam LocalDate displayStartDate,
            @RequestParam(required = false) LocalDate displayEndDate,
            RedirectAttributes redirectAttributes) {
        try {
            Long createdBy = securityUtil.getCurrentUser().getUserId();
            ZoneId jst = ZoneId.of("Asia/Tokyo");
            Instant startInstant = displayStartDate.atStartOfDay(jst).toInstant();
            Instant endInstant = displayEndDate != null
                    ? displayEndDate.atTime(23, 59, 59).atZone(jst).toInstant()
                    : null;
            AdminAnnouncement announcement = AdminAnnouncement.builder()
                    .title(title.strip())
                    .message(message.strip())
                    .isActive(true)
                    .displayStartDate(startInstant)
                    .displayEndDate(endInstant)
                    .createdBy(createdBy)
                    .build();
            adminAnnouncementService.create(announcement);
            redirectAttributes.addFlashAttribute("successMessage", "お知らせを登録しました。");
        } catch (Exception e) {
            log.error("お知らせ登録に失敗しました", e);
            redirectAttributes.addFlashAttribute("errorMessage", "お知らせの登録に失敗しました。");
        }
        return ANNOUNCEMENTS_REDIRECT;
    }

    /**
     * お知らせを更新します。
     *
     * @param announcementId   お知らせID
     * @param title            タイトル
     * @param message          本文
     * @param isActive         有効フラグ
     * @param displayStartDate 表示開始日
     * @param displayEndDate   表示終了日（任意）
     * @param redirectAttributes リダイレクト属性
     * @return リダイレクト先
     */
    @PostMapping("/{announcementId}/update")
    public String updateAnnouncement(
            @PathVariable Long announcementId,
            @RequestParam String title,
            @RequestParam String message,
            @RequestParam(defaultValue = "false") boolean isActive,
            @RequestParam LocalDate displayStartDate,
            @RequestParam(required = false) LocalDate displayEndDate,
            RedirectAttributes redirectAttributes) {
        try {
            ZoneId jst = ZoneId.of("Asia/Tokyo");
            Instant startInstant = displayStartDate.atStartOfDay(jst).toInstant();
            Instant endInstant = displayEndDate != null
                    ? displayEndDate.atTime(23, 59, 59).atZone(jst).toInstant()
                    : null;
            AdminAnnouncement announcement = AdminAnnouncement.builder()
                    .announcementId(announcementId)
                    .title(title.strip())
                    .message(message.strip())
                    .isActive(isActive)
                    .displayStartDate(startInstant)
                    .displayEndDate(endInstant)
                    .build();
            adminAnnouncementService.update(announcement);
            redirectAttributes.addFlashAttribute("successMessage", "お知らせを更新しました。");
        } catch (Exception e) {
            log.error("お知らせ更新に失敗しました: announcementId={}", announcementId, e);
            redirectAttributes.addFlashAttribute("errorMessage", "お知らせの更新に失敗しました。");
        }
        return ANNOUNCEMENTS_REDIRECT;
    }

    /**
     * お知らせを削除します。
     *
     * @param announcementId   お知らせID
     * @param redirectAttributes リダイレクト属性
     * @return リダイレクト先
     */
    @PostMapping("/{announcementId}/delete")
    public String deleteAnnouncement(
            @PathVariable Long announcementId,
            RedirectAttributes redirectAttributes) {
        try {
            adminAnnouncementService.delete(announcementId);
            redirectAttributes.addFlashAttribute("successMessage", "お知らせを削除しました。");
        } catch (Exception e) {
            log.error("お知らせ削除に失敗しました: announcementId={}", announcementId, e);
            redirectAttributes.addFlashAttribute("errorMessage", "お知らせの削除に失敗しました。");
        }
        return ANNOUNCEMENTS_REDIRECT;
    }
}
