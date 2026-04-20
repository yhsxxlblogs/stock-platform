package com.stock.platform.util;

import lombok.extern.slf4j.Slf4j;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * 股市时间工具类
 * 判断当前是否为交易时间
 */
@Slf4j
public class MarketTimeUtil {

    private static final ZoneId CHINA_ZONE = ZoneId.of("Asia/Shanghai");

    /**
     * 判断当前是否为交易时间
     * 交易时间：周一至周五 9:30-11:30, 13:00-15:00
     */
    public static boolean isTradingTime() {
        LocalDateTime now = LocalDateTime.now(CHINA_ZONE);
        return isTradingTime(now);
    }

    /**
     * 判断指定时间是否为交易时间
     */
    public static boolean isTradingTime(LocalDateTime dateTime) {
        // 检查是否为工作日
        DayOfWeek dayOfWeek = dateTime.getDayOfWeek();
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            return false;
        }

        LocalTime time = dateTime.toLocalTime();

        // 上午交易时间 9:30-11:30
        LocalTime morningStart = LocalTime.of(9, 30);
        LocalTime morningEnd = LocalTime.of(11, 30);

        // 下午交易时间 13:00-15:00
        LocalTime afternoonStart = LocalTime.of(13, 0);
        LocalTime afternoonEnd = LocalTime.of(15, 0);

        boolean isMorning = !time.isBefore(morningStart) && !time.isAfter(morningEnd);
        boolean isAfternoon = !time.isBefore(afternoonStart) && !time.isAfter(afternoonEnd);

        return isMorning || isAfternoon;
    }

    /**
     * 判断是否为交易日（不考虑具体时间）
     */
    public static boolean isTradingDay() {
        LocalDateTime now = LocalDateTime.now(CHINA_ZONE);
        DayOfWeek dayOfWeek = now.getDayOfWeek();
        return dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY;
    }

    /**
     * 获取市场状态描述
     */
    public static String getMarketStatus() {
        if (!isTradingDay()) {
            return "休市";
        }

        if (isTradingTime()) {
            return "交易中";
        }

        LocalDateTime now = LocalDateTime.now(CHINA_ZONE);
        LocalTime time = now.toLocalTime();

        // 午休时间
        if (time.isAfter(LocalTime.of(11, 30)) && time.isBefore(LocalTime.of(13, 0))) {
            return "午休";
        }

        // 已收盘
        if (time.isAfter(LocalTime.of(15, 0))) {
            return "已收盘";
        }

        // 未开盘
        if (time.isBefore(LocalTime.of(9, 30))) {
            return "未开盘";
        }

        return "未知";
    }

    /**
     * 获取市场状态代码
     * 0-休市, 1-交易中, 2-午休, 3-已收盘, 4-未开盘
     */
    public static int getMarketStatusCode() {
        if (!isTradingDay()) {
            return 0; // 休市
        }

        if (isTradingTime()) {
            return 1; // 交易中
        }

        LocalDateTime now = LocalDateTime.now(CHINA_ZONE);
        LocalTime time = now.toLocalTime();

        if (time.isAfter(LocalTime.of(11, 30)) && time.isBefore(LocalTime.of(13, 0))) {
            return 2; // 午休
        }

        if (time.isAfter(LocalTime.of(15, 0))) {
            return 3; // 已收盘
        }

        if (time.isBefore(LocalTime.of(9, 30))) {
            return 4; // 未开盘
        }

        return 0;
    }

    /**
     * 获取下次开盘时间
     */
    public static LocalDateTime getNextOpenTime() {
        LocalDateTime now = LocalDateTime.now(CHINA_ZONE);

        // 如果是周末，跳到下周一
        DayOfWeek dayOfWeek = now.getDayOfWeek();
        if (dayOfWeek == DayOfWeek.SATURDAY) {
            return now.plusDays(2).with(LocalTime.of(9, 30));
        }
        if (dayOfWeek == DayOfWeek.SUNDAY) {
            return now.plusDays(1).with(LocalTime.of(9, 30));
        }

        LocalTime time = now.toLocalTime();

        // 如果已经收盘，明天开盘
        if (time.isAfter(LocalTime.of(15, 0))) {
            if (dayOfWeek == DayOfWeek.FRIDAY) {
                return now.plusDays(3).with(LocalTime.of(9, 30));
            }
            return now.plusDays(1).with(LocalTime.of(9, 30));
        }

        // 如果未开盘，今天开盘
        if (time.isBefore(LocalTime.of(9, 30))) {
            return now.with(LocalTime.of(9, 30));
        }

        // 午休时间，下午开盘
        if (time.isAfter(LocalTime.of(11, 30)) && time.isBefore(LocalTime.of(13, 0))) {
            return now.with(LocalTime.of(13, 0));
        }

        return now;
    }
}
