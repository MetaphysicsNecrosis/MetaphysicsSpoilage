package com.metaphysicsnecrosis.metaphysicsspoilage.spoilage;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Система загрузки настроек порчи из JSON файлов
 * Позволяет модпакерам и пользователям переопределять настройки порчи через datapack
 */
public class JsonSpoilageConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(JsonSpoilageConfig.class);
    private static final String CONFIG_PATH = "metaphysicsspoilage/spoilage_config";
    private static final Gson GSON = new Gson();

    /**
     * Кэш загруженных настроек из JSON файлов
     */
    private static final Map<Item, SpoilageData> JSON_CONFIG_CACHE = new HashMap<>();

    /**
     * Структура JSON конфигурации для предмета
     */
    public static class ItemSpoilageConfig {
        public boolean canSpoil = true;
        public int spoilageTime = 2; // дни
        public String spoilageType = "auto"; // auto, meat, plant, bakery
        public boolean excluded = false;

        public ItemSpoilageConfig() {}

        public ItemSpoilageConfig(boolean canSpoil, int spoilageTime, String spoilageType) {
            this.canSpoil = canSpoil;
            this.spoilageTime = spoilageTime;
            this.spoilageType = spoilageType;
        }
    }

    /**
     * Загружает конфигурацию порчи из JSON файлов datapacks
     *
     * @param resourceManager менеджер ресурсов
     */
    public static void loadJsonConfigs(ResourceManager resourceManager) {
        JSON_CONFIG_CACHE.clear();

        try {
            // Загружаем все JSON файлы из папки spoilage_config
            Map<ResourceLocation, Resource> resources = resourceManager.listResources(
                CONFIG_PATH,
                location -> location.getPath().endsWith(".json")
            );

            LOGGER.info("Найдено {} JSON файлов конфигурации порчи", resources.size());

            for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
                ResourceLocation location = entry.getKey();
                Resource resource = entry.getValue();

                try {
                    loadSingleJsonConfig(location, resource);
                } catch (Exception e) {
                    LOGGER.error("Ошибка при загрузке JSON конфигурации {}: {}", location, e.getMessage());
                }
            }

            LOGGER.info("Загружено {} переопределений настроек порчи из JSON", JSON_CONFIG_CACHE.size());

        } catch (Exception e) {
            LOGGER.error("Ошибка при загрузке JSON конфигураций порчи", e);
        }
    }

    /**
     * Загружает один JSON файл конфигурации
     */
    private static void loadSingleJsonConfig(ResourceLocation location, Resource resource) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.open(), StandardCharsets.UTF_8))) {

            JsonElement jsonElement = JsonParser.parseReader(reader);
            if (!jsonElement.isJsonObject()) {
                LOGGER.warn("JSON файл {} не является объектом", location);
                return;
            }

            JsonObject jsonObject = jsonElement.getAsJsonObject();

            // Обрабатываем каждый предмет в JSON файле
            for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
                String itemId = entry.getKey();
                JsonElement configElement = entry.getValue();

                try {
                    processItemConfig(itemId, configElement, location);
                } catch (Exception e) {
                    LOGGER.error("Ошибка при обработке предмета {} в файле {}: {}",
                               itemId, location, e.getMessage());
                }
            }
        }
    }

    /**
     * Обрабатывает конфигурацию одного предмета
     */
    private static void processItemConfig(String itemId, JsonElement configElement, ResourceLocation sourceFile) {
        // Парсим конфигурацию предмета
        ItemSpoilageConfig config;
        try {
            config = GSON.fromJson(configElement, ItemSpoilageConfig.class);
        } catch (Exception e) {
            LOGGER.error("Ошибка при парсинге конфигурации предмета {} из {}: {}",
                       itemId, sourceFile, e.getMessage());
            return;
        }

        // Проверяем валидность item ID
        ResourceLocation itemLocation;
        try {
            itemLocation = ResourceLocation.parse(itemId);
        } catch (Exception e) {
            LOGGER.error("Неверный ID предмета '{}' в файле {}: {}", itemId, sourceFile, e.getMessage());
            return;
        }

        // Получаем предмет из реестра
        if (!BuiltInRegistries.ITEM.containsKey(itemLocation)) {
            LOGGER.warn("Предмет '{}' не найден в реестре (файл: {})", itemId, sourceFile);
            return;
        }
        Item item = BuiltInRegistries.ITEM.getValue(itemLocation);
        if (item == null || item == Items.AIR) {
            LOGGER.warn("Предмет '{}' не найден в реестре (файл: {})", itemId, sourceFile);
            return;
        }

        // Создаем SpoilageData на основе конфигурации
        SpoilageData spoilageData;
        if (config.excluded || !config.canSpoil) {
            spoilageData = SpoilageData.nonSpoilable();
        } else {
            spoilageData = SpoilageData.spoilable(config.spoilageTime);
        }

        // Сохраняем в кэш
        JSON_CONFIG_CACHE.put(item, spoilageData);

        LOGGER.debug("Загружена JSON конфигурация для {}: canSpoil={}, time={}, type={}",
                   itemId, config.canSpoil, config.spoilageTime, config.spoilageType);
    }

    /**
     * Получает настройки порчи для предмета из JSON конфигурации
     *
     * @param item предмет
     * @return настройки порчи или null если не найдено
     */
    public static SpoilageData getJsonSpoilageData(Item item) {
        return JSON_CONFIG_CACHE.get(item);
    }

    /**
     * Проверяет, есть ли JSON конфигурация для предмета
     */
    public static boolean hasJsonConfig(Item item) {
        return JSON_CONFIG_CACHE.containsKey(item);
    }

    /**
     * Очищает кэш JSON конфигураций
     */
    public static void clearCache() {
        JSON_CONFIG_CACHE.clear();
        LOGGER.debug("Кэш JSON конфигураций очищен");
    }

    /**
     * Получает статистику загруженных JSON конфигураций
     */
    public static String getStats() {
        return String.format("JSON конфигурации: %d предметов", JSON_CONFIG_CACHE.size());
    }
}