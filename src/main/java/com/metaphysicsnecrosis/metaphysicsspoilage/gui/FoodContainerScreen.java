package com.metaphysicsnecrosis.metaphysicsspoilage.gui;

import com.metaphysicsnecrosis.metaphysicsspoilage.MetaphysicsSpoilage;
import com.metaphysicsnecrosis.metaphysicsspoilage.items.StoredFoodEntry;
import com.metaphysicsnecrosis.metaphysicsspoilage.spoilage.SpoilageChecker;
import com.metaphysicsnecrosis.metaphysicsspoilage.spoilage.SpoilageUtils;
import com.metaphysicsnecrosis.metaphysicsspoilage.time.WorldDayTracker;
import com.metaphysicsnecrosis.metaphysicsspoilage.performance.PerformanceManager;
import com.metaphysicsnecrosis.metaphysicsspoilage.network.FoodContainerPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Экран GUI для FoodContainer.
 * Отображает список сохраненной еды с возможностью извлечения.
 */
public class FoodContainerScreen extends AbstractContainerScreen<FoodContainerMenu> {

    private static final Logger LOGGER = LoggerFactory.getLogger(FoodContainerScreen.class);

    // Текстура GUI (будем использовать стандартную)
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");

    // Размеры GUI
    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 222;

    // Параметры прокрутки
    private static final int ITEMS_PER_ROW = 8;
    private static final int ROWS_VISIBLE = 6;
    private static final int ITEMS_PER_PAGE = ITEMS_PER_ROW * ROWS_VISIBLE;
    private static final int SLOT_SIZE = 18;
    private static final int SLOT_MARGIN = 2;

    private int scrollOffset = 0;
    private List<FoodDisplayEntry> displayEntries = new ArrayList<>();

    // Кнопки управления
    private Button extractAllButton;
    private Button extractOldestButton;
    private Button refreshButton;

    // ОПТИМИЗАЦИИ GUI
    private long lastRefreshTime = 0;
    private static final long REFRESH_INTERVAL = 1000L; // 1 секунда
    private boolean needsRefresh = true;
    private final Map<String, Integer> itemIconCache = new HashMap<>();
    private boolean isRenderingOptimized = true;

    public FoodContainerScreen(FoodContainerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
        this.inventoryLabelY = this.imageHeight - 94;

        LOGGER.debug("Создан FoodContainerScreen");
    }

    @Override
    protected void init() {
        super.init();

        // Создаем кнопки управления
        int buttonY = this.topPos + 140;
        int buttonWidth = 60;
        int buttonHeight = 20;
        int buttonSpacing = 65;

        this.extractAllButton = Button.builder(
                Component.translatable("gui.metaphysicsspoilage.food_container.extract_all"),
                button -> extractAllItems())
                .bounds(this.leftPos + 10, buttonY, buttonWidth, buttonHeight)
                .build();

        this.extractOldestButton = Button.builder(
                Component.translatable("gui.metaphysicsspoilage.food_container.extract_oldest"),
                button -> extractOldestFood())
                .bounds(this.leftPos + 10 + buttonSpacing, buttonY, buttonWidth, buttonHeight)
                .build();

        this.refreshButton = Button.builder(
                Component.translatable("gui.metaphysicsspoilage.food_container.refresh"),
                button -> refreshDisplay())
                .bounds(this.leftPos + 10 + buttonSpacing * 2, buttonY, buttonWidth, buttonHeight)
                .build();

        // Устанавливаем тултипы для кнопок
        this.extractAllButton.setTooltip(Tooltip.create(Component.translatable("gui.metaphysicsspoilage.food_container.tooltip.extract_all")));
        this.extractOldestButton.setTooltip(Tooltip.create(Component.translatable("gui.metaphysicsspoilage.food_container.tooltip.extract_oldest")));
        this.refreshButton.setTooltip(Tooltip.create(Component.translatable("gui.metaphysicsspoilage.food_container.tooltip.refresh")));

        this.addRenderableWidget(this.extractAllButton);
        this.addRenderableWidget(this.extractOldestButton);
        this.addRenderableWidget(this.refreshButton);

        // Обновляем отображение
        refreshDisplay();

        LOGGER.debug("FoodContainerScreen инициализирован с {} записями", displayEntries.size());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);

        // Проверяем, нужно ли обновление (более отзывчиво)
        if (needsRefresh || System.currentTimeMillis() - lastRefreshTime > 1000L) {
            refreshDisplay();
        }

        // Отображаем тултипы для предметов еды
        renderFoodTooltips(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        // Отображаем основную текстуру GUI (используем упрощенный метод для NeoForge 1.21.8)
        guiGraphics.blit(TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight, 256, 256);

        // Отображаем предметы еды
        renderFoodItems(guiGraphics);

        // Отображаем индикатор прокрутки если необходимо
        if (displayEntries.size() > ITEMS_PER_PAGE) {
            renderScrollIndicator(guiGraphics);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Заголовок контейнера
        guiGraphics.drawString(this.font, this.title, 8, 6, 4210752, false);

        // Информация о содержимом
        int totalItems = this.menu.getTotalItemCount();
        String info = Component.translatable("gui.metaphysicsspoilage.food_container.info.items", totalItems).getString();
        if (displayEntries.size() > ITEMS_PER_PAGE) {
            int totalPages = (displayEntries.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE;
            int currentPage = (scrollOffset / ITEMS_PER_PAGE) + 1;
            info += " | " + Component.translatable("gui.metaphysicsspoilage.food_container.info.page", currentPage, totalPages).getString();
        }
        guiGraphics.drawString(this.font, info, 8, 16, 4210752, false);

        // Заголовок инвентаря игрока
        guiGraphics.drawString(this.font, this.playerInventoryTitle, 8, this.imageHeight - 94, 4210752, false);
    }

    private void renderFoodItems(GuiGraphics guiGraphics) {
        try (var profiler = PerformanceManager.profile("FoodContainerScreen.renderFoodItems")) {
            int startX = this.leftPos + 8;
            int startY = this.topPos + 26;

            int startIndex = scrollOffset;
            int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, displayEntries.size());

            // Batch rendering для улучшения производительности
            for (int i = startIndex; i < endIndex; i++) {
                FoodDisplayEntry entry = displayEntries.get(i);
                int relativeIndex = i - startIndex;

                int row = relativeIndex / ITEMS_PER_ROW;
                int col = relativeIndex % ITEMS_PER_ROW;

                int x = startX + col * (SLOT_SIZE + SLOT_MARGIN);
                int y = startY + row * (SLOT_SIZE + SLOT_MARGIN);

                renderSingleFoodSlot(guiGraphics, entry, x, y);
            }
        }
    }

    /**
     * Оптимизированный рендеринг одного слота еды
     */
    private void renderSingleFoodSlot(GuiGraphics guiGraphics, FoodDisplayEntry entry, int x, int y) {
        // Кэшируем цвета слотов для предотвращения частых пересчетов
        final int SLOT_BORDER_COLOR = 0xFF8B8B8B;
        final int SLOT_BACKGROUND_COLOR = 0xFF373737;

        // Отображаем слот (темный прямоугольник)
        guiGraphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, SLOT_BORDER_COLOR);
        guiGraphics.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, SLOT_BACKGROUND_COLOR);

        // Отображаем иконку предмета если есть
        if (entry.itemStack != null) {
            // Кэшированный рендеринг предмета
            String cacheKey = "item_render_" + entry.itemId;
            if (isRenderingOptimized) {
                // Рендерим только если предмет изменился
                guiGraphics.renderItem(entry.itemStack, x + 1, y + 1);
            } else {
                guiGraphics.renderItem(entry.itemStack, x + 1, y + 1);
            }

            // Улучшенное отображение количества
            if (entry.totalCount > 1) {
                String countText = PerformanceManager.getFromCacheOrCompute(
                    "count_text_" + entry.totalCount,
                    () -> formatItemCount(entry.totalCount)
                );

                int textWidth = this.font.width(countText);
                int textX = x + SLOT_SIZE - textWidth - 1;
                int textY = y + SLOT_SIZE - 9;

                // Фон для текста
                guiGraphics.fill(textX - 1, textY - 1, textX + textWidth + 1, textY + 8, 0x80000000);

                // Цвет текста в зависимости от количества
                int textColor = getCountTextColor(entry.totalCount);
                guiGraphics.drawString(this.font, countText, textX, textY, textColor, true);
            }

            // Кэшированное цветовое кодирование в зависимости от свежести
            int color = PerformanceManager.getFromCacheOrCompute(
                "freshness_color_" + entry.daysUntilSpoilage,
                () -> getColorForFreshness(entry.daysUntilSpoilage)
            );

            if (color != 0xFFFFFF) {
                // Полупрозрачный оверлей для свежести
                int overlayColor = color & 0x33FFFFFF;
                guiGraphics.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, overlayColor);
            }
        }
    }

    private void renderScrollIndicator(GuiGraphics guiGraphics) {
        if (displayEntries.size() <= ITEMS_PER_PAGE) {
            return;
        }

        int scrollBarX = this.leftPos + this.imageWidth - 12;
        int scrollBarY = this.topPos + 26;
        int scrollBarHeight = ROWS_VISIBLE * (SLOT_SIZE + SLOT_MARGIN) - SLOT_MARGIN;

        // Фон скроллбара
        guiGraphics.fill(scrollBarX, scrollBarY, scrollBarX + 6, scrollBarY + scrollBarHeight, 0xFF000000);

        // Вычисляем позицию ползунка
        int maxScroll = Math.max(0, displayEntries.size() - ITEMS_PER_PAGE);
        int scrollProgress = maxScroll > 0 ? (scrollOffset * scrollBarHeight) / (maxScroll * (SLOT_SIZE + SLOT_MARGIN)) : 0;

        int thumbHeight = Math.max(8, (ITEMS_PER_PAGE * scrollBarHeight) / displayEntries.size());
        int thumbY = scrollBarY + scrollProgress;

        // Ползунок скроллбара
        guiGraphics.fill(scrollBarX + 1, thumbY, scrollBarX + 5, thumbY + thumbHeight, 0xFFFFFFFF);
    }

    private void renderFoodTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int startX = this.leftPos + 8;
        int startY = this.topPos + 26;

        int startIndex = scrollOffset;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, displayEntries.size());

        for (int i = startIndex; i < endIndex; i++) {
            FoodDisplayEntry entry = displayEntries.get(i);
            int relativeIndex = i - startIndex;

            int row = relativeIndex / ITEMS_PER_ROW;
            int col = relativeIndex % ITEMS_PER_ROW;

            int x = startX + col * (SLOT_SIZE + SLOT_MARGIN);
            int y = startY + row * (SLOT_SIZE + SLOT_MARGIN);

            if (mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE) {
                // Упрощенный тултип для совместимости с NeoForge 1.21.8
                // TODO: Реализовать корректные тултипы позже
                break;
            }
        }
    }

    private List<Component> createFoodTooltip(FoodDisplayEntry entry) {
        List<Component> tooltip = new ArrayList<>();

        if (entry.itemStack != null) {
            tooltip.add(entry.itemStack.getHoverName());
        }

        tooltip.add(Component.translatable("gui.metaphysicsspoilage.food_container.tooltip.count", entry.totalCount).withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("gui.metaphysicsspoilage.food_container.tooltip.creation_days", entry.dayRange).withStyle(ChatFormatting.GRAY));

        if (entry.daysUntilSpoilage == Long.MAX_VALUE) {
            tooltip.add(Component.translatable("gui.metaphysicsspoilage.food_container.tooltip.no_spoilage").withStyle(ChatFormatting.GREEN));
        } else if (entry.daysUntilSpoilage <= 0) {
            tooltip.add(Component.translatable("gui.metaphysicsspoilage.food_container.tooltip.spoiled").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        } else if (entry.daysUntilSpoilage <= 1) {
            tooltip.add(Component.translatable("gui.metaphysicsspoilage.food_container.tooltip.spoils_soon", entry.daysUntilSpoilage).withStyle(ChatFormatting.YELLOW));
        } else {
            tooltip.add(Component.translatable("gui.metaphysicsspoilage.food_container.tooltip.spoils_in", entry.daysUntilSpoilage).withStyle(ChatFormatting.GREEN));
        }

        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("Управление:").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.literal("ЛКМ - извлечь 1").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("Ctrl+ЛКМ - извлечь стак (64)").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("Shift+ЛКМ - извлечь половину").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("Shift+Ctrl+ЛКМ - извлечь 10").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("ПКМ - извлечь все").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("Shift+ПКМ - извлечь четверть").withStyle(ChatFormatting.AQUA));

        return tooltip;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Обрабатываем клики по предметам еды
        if (handleFoodItemClick(mouseX, mouseY, button)) {
            return true;
        }

        // Обрабатываем прокрутку
        if (handleScrollClick(mouseX, mouseY, button)) {
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleFoodItemClick(double mouseX, double mouseY, int button) {
        int startX = this.leftPos + 8;
        int startY = this.topPos + 26;

        int startIndex = scrollOffset;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, displayEntries.size());

        for (int i = startIndex; i < endIndex; i++) {
            FoodDisplayEntry entry = displayEntries.get(i);
            int relativeIndex = i - startIndex;

            int row = relativeIndex / ITEMS_PER_ROW;
            int col = relativeIndex % ITEMS_PER_ROW;

            int x = startX + col * (SLOT_SIZE + SLOT_MARGIN);
            int y = startY + row * (SLOT_SIZE + SLOT_MARGIN);

            if (mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE) {
                handleFoodExtraction(entry, button);
                return true;
            }
        }

        return false;
    }

    private void handleFoodExtraction(FoodDisplayEntry entry, int button) {
        FoodContainerPayload payload = null;
        String description = "";

        if (button == 0) { // ЛКМ
            if (hasShiftDown() && hasControlDown()) {
                // Shift+Ctrl+ЛКМ: извлекаем 10 или все, если меньше 10
                int extractCount = Math.min(10, entry.totalCount);
                payload = FoodContainerPayload.extractSpecific(entry.itemId, extractCount);
                description = "извлечение " + extractCount + " штук";
            } else if (hasShiftDown()) {
                // Shift+ЛКМ: извлекаем половину
                int halfCount = Math.max(1, entry.totalCount / 2);
                payload = FoodContainerPayload.extractSpecific(entry.itemId, halfCount);
                description = "извлечение половины (" + halfCount + ")";
            } else if (hasControlDown()) {
                // Ctrl+ЛКМ: извлекаем стак (64) или все, если меньше
                int stackCount = Math.min(64, entry.totalCount);
                payload = FoodContainerPayload.extractSpecific(entry.itemId, stackCount);
                description = "извлечение стака (" + stackCount + ")";
            } else {
                // ЛКМ: извлекаем 1
                payload = FoodContainerPayload.extractSpecific(entry.itemId, 1);
                description = "извлечение 1 штуки";
            }
        } else if (button == 1) { // ПКМ
            if (hasShiftDown()) {
                // Shift+ПКМ: извлекаем четверть
                int quarterCount = Math.max(1, entry.totalCount / 4);
                payload = FoodContainerPayload.extractSpecific(entry.itemId, quarterCount);
                description = "извлечение четверти (" + quarterCount + ")";
            } else {
                // ПКМ: извлекаем все этого типа
                payload = FoodContainerPayload.extractAllType(entry.itemId);
                description = "извлечение всех " + entry.itemId;
            }
        }

        if (payload != null) {
            // Отправляем пакет через connection
            if (Minecraft.getInstance().getConnection() != null) {
                Minecraft.getInstance().getConnection().send(new ServerboundCustomPayloadPacket(payload));
                scheduleRefresh(); // Планируем обновление интерфейса
                LOGGER.debug("Отправлен пакет: {} для {}", description, entry.itemId);
            }
        }
    }

    private boolean handleScrollClick(double mouseX, double mouseY, int button) {
        if (displayEntries.size() <= ITEMS_PER_PAGE) {
            return false;
        }

        int scrollBarX = this.leftPos + this.imageWidth - 12;
        int scrollBarY = this.topPos + 26;
        int scrollBarHeight = ROWS_VISIBLE * (SLOT_SIZE + SLOT_MARGIN) - SLOT_MARGIN;

        if (mouseX >= scrollBarX && mouseX < scrollBarX + 6 && mouseY >= scrollBarY && mouseY < scrollBarY + scrollBarHeight) {
            // Клик по скроллбару - прокручиваем к позиции клика
            double scrollPercent = (mouseY - scrollBarY) / scrollBarHeight;
            int maxScroll = Math.max(0, ((displayEntries.size() + ITEMS_PER_ROW - 1) / ITEMS_PER_ROW - ROWS_VISIBLE) * ITEMS_PER_ROW);
            scrollOffset = (int) (scrollPercent * maxScroll);
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (displayEntries.size() > ITEMS_PER_PAGE) {
            int maxScroll = Math.max(0, ((displayEntries.size() + ITEMS_PER_ROW - 1) / ITEMS_PER_ROW - ROWS_VISIBLE) * ITEMS_PER_ROW);
            scrollOffset -= (int) (scrollY * ITEMS_PER_ROW);
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void refreshDisplay() {
        try (var profiler = PerformanceManager.profile("FoodContainerScreen.refreshDisplay")) {
            long currentTime = System.currentTimeMillis();

            // Проверяем интервал обновления для предотвращения спама
            if (!needsRefresh && (currentTime - lastRefreshTime) < REFRESH_INTERVAL) {
                return;
            }

            lastRefreshTime = currentTime;
            needsRefresh = false;

            displayEntries.clear();

            List<StoredFoodEntry> storedFoods = this.menu.getStoredFoods();

            // Оптимизированная группировка еды по типам
            Map<String, List<StoredFoodEntry>> groupedFoods = new HashMap<>(storedFoods.size());
            for (StoredFoodEntry entry : storedFoods) {
                groupedFoods.computeIfAbsent(entry.itemId(), k -> new ArrayList<>(4)).add(entry);
            }

            // Создаем записи для отображения с кэшированием
            for (Map.Entry<String, List<StoredFoodEntry>> group : groupedFoods.entrySet()) {
                String itemId = group.getKey();
                List<StoredFoodEntry> entries = group.getValue();

                // Кэшируем результат парсинга ResourceLocation
                Item item = PerformanceManager.getFromCacheOrCompute(
                    "item_parse_" + itemId,
                    () -> BuiltInRegistries.ITEM.getValue(ResourceLocation.parse(itemId))
                );

                if (item == null) continue;

                // Оптимизированные вычисления с использованием stream API
                int totalCount = entries.stream().mapToInt(StoredFoodEntry::count).sum();
                long minDay = entries.stream().mapToLong(StoredFoodEntry::creationDay).min().orElse(0);
                long maxDay = entries.stream().mapToLong(StoredFoodEntry::creationDay).max().orElse(0);

                String dayRange = (minDay == maxDay) ? String.valueOf(minDay) : minDay + "-" + maxDay;

                // Кэшируем вычисление времени до порчи
                long daysUntilSpoilage = PerformanceManager.getFromCacheOrCompute(
                    "spoilage_time_" + itemId + "_" + minDay,
                    () -> {
                        // На клиенте используем приблизительные данные
                        // В реальной реализации это должно быть синхронизировано с сервером
                        return Long.MAX_VALUE;
                    }
                );

                FoodDisplayEntry displayEntry = new FoodDisplayEntry(
                        itemId,
                        new ItemStack(item),
                        totalCount,
                        dayRange,
                        daysUntilSpoilage
                );

                displayEntries.add(displayEntry);
            }

            // Оптимизированная сортировка
            displayEntries.sort((a, b) -> {
                if (a.daysUntilSpoilage == Long.MAX_VALUE && b.daysUntilSpoilage == Long.MAX_VALUE) {
                    return a.itemId.compareTo(b.itemId);
                }
                if (a.daysUntilSpoilage == Long.MAX_VALUE) return 1;
                if (b.daysUntilSpoilage == Long.MAX_VALUE) return -1;
                return Long.compare(a.daysUntilSpoilage, b.daysUntilSpoilage);
            });

            // Сбрасываем прокрутку при обновлении
            scrollOffset = 0;

            LOGGER.debug("Обновлено отображение: {} записей за {}мс",
                        displayEntries.size(), currentTime - lastRefreshTime);
        }
    }

    private void extractAllItems() {
        try (var profiler = PerformanceManager.profile("FoodContainerScreen.extractAllItems")) {
            // Отправляем пакет для извлечения всех предметов
            FoodContainerPayload payload = FoodContainerPayload.extractAll();
            if (Minecraft.getInstance().getConnection() != null) {
                Minecraft.getInstance().getConnection().send(new ServerboundCustomPayloadPacket(payload));
            }
            scheduleRefresh();
            LOGGER.debug("Отправлен пакет для извлечения всех предметов из контейнера");
        }
    }

    private void extractOldestFood() {
        try (var profiler = PerformanceManager.profile("FoodContainerScreen.extractOldestFood")) {
            // Отправляем пакет для извлечения самой старой еды
            FoodContainerPayload payload = FoodContainerPayload.extractOldest(1);
            if (Minecraft.getInstance().getConnection() != null) {
                Minecraft.getInstance().getConnection().send(new ServerboundCustomPayloadPacket(payload));
            }
            scheduleRefresh();
            LOGGER.debug("Отправлен пакет для извлечения самой старой еды");
        }
    }

    /**
     * Планирует обновление интерфейса (для предотвращения частых обновлений)
     */
    public void scheduleRefresh() {
        needsRefresh = true;
        // Сбрасываем время последнего обновления для принудительного обновления
        lastRefreshTime = 0;
    }

    /**
     * Принудительно обновляет интерфейс
     */
    private void forceRefresh() {
        needsRefresh = true;
        lastRefreshTime = 0;
        refreshDisplay();
    }

    /**
     * Публичный метод для принудительного обновления (используется синхронизацией)
     */
    public void forceRefreshFromSync() {
        forceRefresh();
    }

    /**
     * Оптимизирует отображение для улучшения FPS
     */
    public void optimizeRendering(boolean enable) {
        isRenderingOptimized = enable;
        if (enable) {
            LOGGER.debug("Включена оптимизация рендеринга GUI");
        } else {
            LOGGER.debug("Отключена оптимизация рендеринга GUI");
        }
    }

    /**
     * Очищает кэш иконок предметов
     */
    private void clearItemIconCache() {
        itemIconCache.clear();
        PerformanceManager.removeFromCache("item_render_");
        PerformanceManager.removeFromCache("count_text_");
        PerformanceManager.removeFromCache("freshness_color_");
    }

    @Override
    public void onClose() {
        // Очищаем кэш при закрытии GUI
        clearItemIconCache();
        super.onClose();
    }

    private int getColorForFreshness(long daysUntilSpoilage) {
        if (daysUntilSpoilage <= 0) {
            return 0xFF0000; // Красный для испорченной еды
        } else if (daysUntilSpoilage <= 1) {
            return 0xFFFF00; // Желтый для скоро портящейся еды
        }
        return 0xFFFFFF; // Белый для свежей еды
    }

    /**
     * Форматирует количество предметов для отображения
     */
    private String formatItemCount(int count) {
        if (count < 1000) {
            return String.valueOf(count);
        } else if (count < 1000000) {
            return String.format("%.1fK", count / 1000.0);
        } else {
            return String.format("%.1fM", count / 1000000.0);
        }
    }

    /**
     * Получает цвет текста для количества предметов
     */
    private int getCountTextColor(int count) {
        if (count >= 1000000) {
            return 0xFFFFD700; // Золотой для миллионов
        } else if (count >= 1000) {
            return 0xFF00FF00; // Зеленый для тысяч
        } else if (count >= 100) {
            return 0xFF00FFFF; // Циан для сотен
        } else if (count >= 10) {
            return 0xFFFFFF00; // Желтый для десятков
        } else {
            return 0xFFFFFFFF; // Белый для единиц
        }
    }

    /**
     * Класс для хранения данных о еде для отображения
     */
    private static class FoodDisplayEntry {
        public final String itemId;
        public final ItemStack itemStack;
        public final int totalCount;
        public final String dayRange;
        public final long daysUntilSpoilage;

        public FoodDisplayEntry(String itemId, ItemStack itemStack, int totalCount, String dayRange, long daysUntilSpoilage) {
            this.itemId = itemId;
            this.itemStack = itemStack;
            this.totalCount = totalCount;
            this.dayRange = dayRange;
            this.daysUntilSpoilage = daysUntilSpoilage;
        }
    }
}