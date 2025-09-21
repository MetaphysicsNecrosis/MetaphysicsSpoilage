package com.metaphysicsnecrosis.metaphysicsspoilage.spoilage;

/**
 * Класс для хранения данных о порче предметов.
 * Содержит информацию о времени порчи и других параметрах.
 */
public class SpoilageData {

    private final long spoilageTime;
    private final boolean canSpoil;

    /**
     * Конструктор для данных о порче
     *
     * @param spoilageTime Время в днях до полной порчи предмета
     * @param canSpoil Может ли предмет портиться
     */
    public SpoilageData(long spoilageTime, boolean canSpoil) {
        this.spoilageTime = spoilageTime;
        this.canSpoil = canSpoil;
    }

    /**
     * Конструктор для предметов, которые не могут портиться
     */
    public SpoilageData() {
        this.spoilageTime = 0;
        this.canSpoil = false;
    }

    /**
     * Получить время порчи в днях
     *
     * @return Количество дней до полной порчи
     */
    public long getSpoilageTime() {
        return spoilageTime;
    }

    /**
     * Проверить, может ли предмет портиться
     *
     * @return true, если предмет может портиться
     */
    public boolean canSpoil() {
        return canSpoil;
    }

    /**
     * Статический метод для создания данных о не портящихся предметах
     *
     * @return SpoilageData для предметов, которые не портятся
     */
    public static SpoilageData nonSpoilable() {
        return new SpoilageData();
    }

    /**
     * Статический метод для создания данных о портящихся предметах
     *
     * @param spoilageTime Время до порчи в днях
     * @return SpoilageData для портящихся предметов
     */
    public static SpoilageData spoilable(long spoilageTime) {
        return new SpoilageData(spoilageTime, true);
    }

    @Override
    public String toString() {
        return "SpoilageData{" +
                "spoilageTime=" + spoilageTime +
                ", canSpoil=" + canSpoil +
                '}';
    }
}