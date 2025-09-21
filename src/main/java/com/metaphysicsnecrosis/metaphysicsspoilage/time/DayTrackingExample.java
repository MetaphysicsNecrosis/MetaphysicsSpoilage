package com.metaphysicsnecrosis.metaphysicsspoilage.time;

import net.minecraft.server.level.ServerLevel;
import com.metaphysicsnecrosis.metaphysicsspoilage.MetaphysicsSpoilage;

/**
 * Пример использования системы отслеживания дней.
 * Этот класс демонстрирует различные способы работы с WorldDayTracker и TimeUtils.
 */
public class DayTrackingExample {

    /**
     * Пример получения информации о текущем дне
     */
    public static void logDayInformation(ServerLevel level) {
        WorldDayTracker tracker = WorldDayTracker.getInstance(level);

        long currentDay = tracker.getCurrentDay();
        long worldDay = TimeUtils.getCurrentDayFromWorldTime(level);
        long timeOfDay = TimeUtils.getTimeOfDay(level);
        boolean isDaytime = TimeUtils.isDaytime(level);
        double dayProgress = TimeUtils.getDayProgress(level);

        MetaphysicsSpoilage.LOGGER.info("=== Day Information ===");
        MetaphysicsSpoilage.LOGGER.info("Tracked Day: {}", currentDay);
        MetaphysicsSpoilage.LOGGER.info("World Day: {}", worldDay);
        MetaphysicsSpoilage.LOGGER.info("Time of Day: {} ticks", timeOfDay);
        MetaphysicsSpoilage.LOGGER.info("Is Daytime: {}", isDaytime);
        MetaphysicsSpoilage.LOGGER.info("Day Progress: {:.2f}%", dayProgress * 100);
        MetaphysicsSpoilage.LOGGER.info("Game Hour: {}", TimeUtils.ticksToHours(timeOfDay));
    }

    /**
     * Пример проверки, прошло ли определенное количество дней
     */
    public static boolean hasPassedDays(ServerLevel level, long startDay, long requiredDays) {
        WorldDayTracker tracker = WorldDayTracker.getInstance(level);
        long currentDay = tracker.getCurrentDay();
        long passedDays = currentDay - startDay;

        MetaphysicsSpoilage.LOGGER.info("Days passed since day {}: {}/{}",
            startDay, passedDays, requiredDays);

        return passedDays >= requiredDays;
    }

    /**
     * Пример установки определенного дня для тестирования
     */
    public static void setTestDay(ServerLevel level, long day) {
        MetaphysicsSpoilage.LOGGER.info("Setting test day to: {}", day);
        TimeUtils.setDay(level, day);

        WorldDayTracker tracker = WorldDayTracker.getInstance(level);
        MetaphysicsSpoilage.LOGGER.info("Day tracker now shows: {}", tracker.getCurrentDay());
    }

    /**
     * Пример симуляции прохождения времени
     */
    public static void simulateDayPassing(ServerLevel level) {
        WorldDayTracker tracker = WorldDayTracker.getInstance(level);
        long startDay = tracker.getCurrentDay();

        MetaphysicsSpoilage.LOGGER.info("Simulating day passing from day {}", startDay);

        // Увеличиваем день вручную (для тестирования)
        tracker.incrementDay();

        MetaphysicsSpoilage.LOGGER.info("Day manually incremented to: {}", tracker.getCurrentDay());
    }

    /**
     * Пример проверки времени суток для разных игровых механик
     */
    public static void checkTimeBasedConditions(ServerLevel level) {
        boolean isDaytime = TimeUtils.isDaytime(level);
        boolean isNighttime = TimeUtils.isNighttime(level);
        int currentHour = TimeUtils.ticksToHours(TimeUtils.getTimeOfDay(level));

        if (isDaytime) {
            MetaphysicsSpoilage.LOGGER.info("It's daytime (hour {}), perfect for spoilage checks!", currentHour);
        } else {
            MetaphysicsSpoilage.LOGGER.info("It's nighttime (hour {}), spoilage might accelerate!", currentHour);
        }

        // Пример условий для разного времени дня
        if (currentHour >= 6 && currentHour <= 12) {
            MetaphysicsSpoilage.LOGGER.info("Morning period - fresh items!");
        } else if (currentHour >= 12 && currentHour <= 18) {
            MetaphysicsSpoilage.LOGGER.info("Afternoon period - normal spoilage rate");
        } else {
            MetaphysicsSpoilage.LOGGER.info("Evening/Night period - accelerated spoilage!");
        }
    }
}