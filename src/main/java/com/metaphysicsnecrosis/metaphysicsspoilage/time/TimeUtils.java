package com.metaphysicsnecrosis.metaphysicsspoilage.time;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Утилитный класс для работы с игровым временем
 */
public class TimeUtils {

    /** Количество тиков в одном игровом дне Minecraft */
    public static final long TICKS_PER_DAY = 24000L;

    /** Количество тиков в одной игровой минуте */
    public static final long TICKS_PER_MINUTE = 1000L;

    /** Количество тиков в одном игровом часе */
    public static final long TICKS_PER_HOUR = 1000L;

    /**
     * Получить текущий игровой день из мирового времени
     * @param level игровой мир
     * @return номер текущего дня (начиная с 0)
     */
    public static long getCurrentDayFromWorldTime(Level level) {
        return level.getDayTime() / TICKS_PER_DAY;
    }

    /**
     * Получить время суток в тиках (0-23999)
     * @param level игровой мир
     * @return время суток в тиках
     */
    public static long getTimeOfDay(Level level) {
        return level.getDayTime() % TICKS_PER_DAY;
    }

    /**
     * Проверить, наступил ли новый день по сравнению с сохраненным днем
     * @param level серверный уровень
     * @param savedDay сохраненный день
     * @return true, если наступил новый день
     */
    public static boolean isNewDay(Level level, long savedDay) {
        return getCurrentDayFromWorldTime(level) > savedDay;
    }

    /**
     * Получить количество дней, прошедших с указанного дня
     * @param level игровой мир
     * @param fromDay день, с которого считать
     * @return количество прошедших дней
     */
    public static long getDaysSince(Level level, long fromDay) {
        long currentDay = getCurrentDayFromWorldTime(level);
        return Math.max(0, currentDay - fromDay);
    }

    /**
     * Проверить, день ли сейчас (6000-18000 тиков)
     * @param level игровой мир
     * @return true, если сейчас день
     */
    public static boolean isDaytime(Level level) {
        long timeOfDay = getTimeOfDay(level);
        return timeOfDay >= 0 && timeOfDay < 12000;
    }

    /**
     * Проверить, ночь ли сейчас (18000-6000 тиков)
     * @param level игровой мир
     * @return true, если сейчас ночь
     */
    public static boolean isNighttime(Level level) {
        return !isDaytime(level);
    }

    /**
     * Конвертировать тики в игровые часы
     * @param ticks количество тиков
     * @return игровые часы (0-23)
     */
    public static int ticksToHours(long ticks) {
        return (int) ((ticks % TICKS_PER_DAY) / TICKS_PER_HOUR);
    }

    /**
     * Конвертировать игровые часы в тики
     * @param hours игровые часы (0-23)
     * @return количество тиков
     */
    public static long hoursToTicks(int hours) {
        return (hours % 24) * TICKS_PER_HOUR;
    }

    /**
     * Получить прогресс дня (0.0 - 1.0)
     * @param level игровой мир
     * @return прогресс дня от 0.0 (начало дня) до 1.0 (конец дня)
     */
    public static double getDayProgress(Level level) {
        return (double) getTimeOfDay(level) / TICKS_PER_DAY;
    }

    /**
     * Синхронизировать WorldDayTracker с мировым временем
     * @param level серверный уровень
     * @return true, если день был обновлен
     */
    public static boolean synchronizeDayTracker(ServerLevel level) {
        if (level.isClientSide()) {
            return false;
        }

        WorldDayTracker tracker = WorldDayTracker.getInstance(level);
        long worldDay = getCurrentDayFromWorldTime(level);
        long trackedDay = tracker.getCurrentDay();

        if (worldDay > trackedDay) {
            tracker.setCurrentDay(worldDay);
            return true;
        }

        return false;
    }

    /**
     * Принудительно установить день в WorldDayTracker и мировом времени
     * @param level серверный уровень
     * @param day день для установки
     */
    public static void setDay(ServerLevel level, long day) {
        if (level.isClientSide()) {
            return;
        }

        WorldDayTracker tracker = WorldDayTracker.getInstance(level);
        tracker.setCurrentDay(day);

        // Сохраняем текущее время суток
        long timeOfDay = getTimeOfDay(level);
        level.setDayTime(day * TICKS_PER_DAY + timeOfDay);
    }
}