package com.attendance.app.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.attendance.app.entity.Holiday;
import com.attendance.app.mapper.HolidayMapper;
import com.attendance.app.util.DateTimeUtil;

import lombok.RequiredArgsConstructor;

/**
 * 祝日 CSV の取り込みと祝日マスタ更新を扱うサービスです。
 */
@Service
@RequiredArgsConstructor
public class HolidayService {

    private final HolidayMapper holidayMapper;

    /**
     * 祝日マスタを日付セットとして読み込みます。
     */
    public Set<LocalDate> loadHolidays() {
        List<Holiday> list = holidayMapper.selectAll();
        if (list == null || list.isEmpty()) return new HashSet<>();
        return list.stream().map(h -> h.getHolidayDate()).collect(Collectors.toSet());
    }

    /**
     * 指定された年の祝日をキャッシュから取得します。
     *
     * @param year 対象年
     * @return 祝日の日付セット
     */
    @Cacheable("holidays")
    public Set<LocalDate> getHolidaysByYear(int year) {
        List<Holiday> list = holidayMapper.selectByYear(year);
        if (list == null || list.isEmpty()) return new HashSet<>();
        return list.stream().map(h -> h.getHolidayDate()).collect(Collectors.toSet());
    }

    /**
     * 登録済み祝日を一覧で取得します。
     */
    public List<Holiday> getAllHolidays() {
        List<Holiday> list = holidayMapper.selectAll();
        if (list == null || list.isEmpty()) return new java.util.ArrayList<>();
        return list;
    }

    /**
     * CSV ファイルから祝日一覧をパースします。
     */
    public List<Holiday> parseFromCsv(MultipartFile file) throws IOException {
        List<Holiday> holidays = new java.util.ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split(",");
                LocalDate d = LocalDate.parse(parts[0]);
                String name = parts.length > 1 ? parts[1] : "";
                Holiday h = Holiday.builder()
                        .holidayDate(d)
                        .name(name)
                        .createdAt(DateTimeUtil.now())
                        .build();
                holidays.add(h);
            }
        }
        return holidays;
    }

    /**
     * 既存祝日を全削除して、指定一覧で置き換えます。
     */
    @Transactional
    @CacheEvict(value = "holidays", allEntries = true)
    public void saveHolidays(List<Holiday> holidays) {
        // replace all holidays with new list
        holidayMapper.deleteAll();
        for (Holiday h : holidays) {
            holidayMapper.insert(h);
        }
    }

    /**
     * CSV ファイルを読み込んで祝日マスタを置き換えます。
     */
    @Transactional
    public void saveFromCsv(MultipartFile file) throws IOException {
        List<Holiday> holidays = parseFromCsv(file);
        saveHolidays(holidays);
    }

}