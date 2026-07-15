package com.attendance.app.controller;

import com.attendance.app.entity.AdminAnnouncement;
import com.attendance.app.security.SecurityUtil;
import com.attendance.app.service.AdminAnnouncementNotFoundException;
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
import java.time.LocalTime;
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
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int PAGE_WINDOW_SIZE = 5;
    private static final ZoneId JST_ZONE_ID = ZoneId.of("Asia/Tokyo");

    private final AdminAnnouncementService adminAnnouncementService;
    private final SecurityUtil securityUtil;

    /**
     * お知らせ一覧画面を表示します。
     *
     * @param model Spring MVC のモデル
     * @return テンプレート名 (admin/announcements)
     */
    @GetMapping
    public String listAnnouncements(
            @RequestParam(defaultValue = "0") String page,
            @RequestParam(defaultValue = "10") String size,
            Model model) {
        int pageSize = parsePageSize(size);
        long totalCount = adminAnnouncementService.countAnnouncements();
        long totalPages = totalCount == 0 ? 0 : ((totalCount - 1) / pageSize) + 1;
        long requestedPage = parseNonNegativeLong(page, 0);
        long currentPage = totalPages == 0 ? 0 : Math.min(requestedPage, totalPages - 1);
        long pageStart = Math.max(0, Math.min(currentPage - 2, totalPages - PAGE_WINDOW_SIZE));
        long pageEnd = Math.min(totalPages, pageStart + PAGE_WINDOW_SIZE);

        model.addAttribute("announcements",
                adminAnnouncementService.getAnnouncementsPage(currentPage * pageSize, pageSize));
        model.addAttribute("newAnnouncement", new AdminAnnouncement());
        model.addAttribute("today", DateTimeUtil.todayJapan());
        model.addAttribute("jstZoneId", JST_ZONE_ID);
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("pageSize", pageSize);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalAnnouncementCount", totalCount);
        model.addAttribute("pageNumbers", java.util.stream.LongStream.range(pageStart, pageEnd).boxed().toList());
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
        if (displayEndDate != null && displayEndDate.isBefore(displayStartDate)) {
            redirectAttributes.addFlashAttribute("errorMessage", "表示終了日は表示開始日より後の日付を指定してください。");
            return ANNOUNCEMENTS_REDIRECT;
        }
        try {
            Long createdBy = securityUtil.getCurrentUser().getUserId();
            Instant startInstant = DateTimeUtil.toInstant(displayStartDate);
            Instant endInstant = displayEndDate != null
                    ? DateTimeUtil.toInstant(displayEndDate, LocalTime.of(23, 59, 59))
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
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
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
            @RequestParam(defaultValue = "0") String page,
            @RequestParam(defaultValue = "10") String size,
            RedirectAttributes redirectAttributes) {
        addPaginationRedirectAttributes(page, size, redirectAttributes);
        if (displayEndDate != null && displayEndDate.isBefore(displayStartDate)) {
            redirectAttributes.addFlashAttribute("errorMessage", "表示終了日は表示開始日より後の日付を指定してください。");
            return ANNOUNCEMENTS_REDIRECT;
        }
        try {
            Instant startInstant = DateTimeUtil.toInstant(displayStartDate);
            Instant endInstant = displayEndDate != null
                    ? DateTimeUtil.toInstant(displayEndDate, LocalTime.of(23, 59, 59))
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
        } catch (AdminAnnouncementNotFoundException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "対象のお知らせが見つかりません。");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
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
            @RequestParam(defaultValue = "0") String page,
            @RequestParam(defaultValue = "10") String size,
            RedirectAttributes redirectAttributes) {
        addPaginationRedirectAttributes(page, size, redirectAttributes);
        try {
            adminAnnouncementService.delete(announcementId);
            redirectAttributes.addFlashAttribute("successMessage", "お知らせを削除しました。");
        } catch (AdminAnnouncementNotFoundException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "対象のお知らせが見つかりません。");
        } catch (Exception e) {
            log.error("お知らせ削除に失敗しました: announcementId={}", announcementId, e);
            redirectAttributes.addFlashAttribute("errorMessage", "お知らせの削除に失敗しました。");
        }
        return ANNOUNCEMENTS_REDIRECT;
    }

    private int parsePageSize(String value) {
        long parsed = parseNonNegativeLong(value, DEFAULT_PAGE_SIZE);
        if (parsed < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return (int) Math.min(parsed, AdminAnnouncementService.MAX_PAGE_SIZE);
    }

    private long parseNonNegativeLong(String value, long defaultValue) {
        try {
            return Math.max(0, Long.parseLong(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private void addPaginationRedirectAttributes(
            String page, String size, RedirectAttributes redirectAttributes) {
        redirectAttributes.addAttribute("page", parseNonNegativeLong(page, 0));
        redirectAttributes.addAttribute("size", parsePageSize(size));
    }
}
