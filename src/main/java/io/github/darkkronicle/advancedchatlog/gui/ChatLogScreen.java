/*
 * Copyright (C) 2021-2025 DarkKronicle
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.github.darkkronicle.advancedchatlog.gui;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.util.InfoUtils;
import fi.dy.masa.malilib.util.StringUtils;
import io.github.darkkronicle.advancedchatcore.chat.ChatMessage;
import io.github.darkkronicle.advancedchatcore.config.ConfigStorage;
import io.github.darkkronicle.advancedchatcore.gui.ContextMenu;
import io.github.darkkronicle.advancedchatcore.util.Colors;
import io.github.darkkronicle.advancedchatcore.util.FindType;
import io.github.darkkronicle.advancedchatcore.util.ModifierKeyUtil;
import io.github.darkkronicle.advancedchatcore.util.SearchUtils;
import io.github.darkkronicle.advancedchatlog.AdvancedChatLog;
import io.github.darkkronicle.advancedchatlog.ChatLogData;
import io.github.darkkronicle.advancedchatlog.config.ChatLogConfigStorage;
import io.github.darkkronicle.advancedchatlog.mixin.ScreenInvoker;
import io.github.darkkronicle.advancedchatlog.util.LogChatMessage;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.PatternSyntaxException;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.ChatFormatting;
import net.minecraft.util.Util;
import org.apache.logging.log4j.Level;

@Environment(EnvType.CLIENT)
public class ChatLogScreen extends GuiBase {

    /** The px where the scroll will start */
    private double scrollStart = 0;

    /** The px where the scroll will end */
    private double scrollEnd = 0;

    /** The current value of scroll. This should be used to grab scroll value. */
    private double currentScroll = 0;

    /** Last time scroll was updated. Used for smooth scroll. */
    private long lastScrollTime = 0;

    private ContextMenu menu = null;
    private LogChatMessage message = null;

    private List<ChatMessage.AdvancedChatLine> renderLines;
    private GuiTextFieldGeneric search = null;
    private TextFieldRunnable send = null;
    private ButtonGeneric searchType = null;
    private FindType findType = FindType.LITERAL;

    public ChatLogScreen() {
        super();
    }

    public void add(LogChatMessage message) {
        add(message.getMessage());
        if (currentScroll > 0) {
            currentScroll += message.getMessage().getLineCount() * (minecraft.font.lineHeight + 2);
        }
    }

    public void add(ChatMessage message) {
        try {
            if (SearchUtils.isMatch(
                    message.getDisplayText().getString(), search.getValue(), findType)) {
                for (int i = 0; i < message.getLineCount(); i++) {
                    renderLines.add(0, message.getLines().get(i));
                }
            }
        } catch (PatternSyntaxException e) {
            // Already handled earlier.
        }
    }

    @Override
    public void initGui() {
        super.initGui();
        setLines(ChatLogData.getInstance().getMessages());
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        search = new GuiTextFieldGeneric((width / 2) - 70, 6, 141, 20, font);
        addTextField(
                search,
                (textField -> {
                    searchText(textField.getValue());
                    return true;
                })
        );
        searchType = new ButtonGeneric(width / 2 + 72, 6, 70, false, findType.getDisplayName());
        addButton(
                searchType,
                ((button, mouseButton) -> {
                    if (mouseButton == 0) {
                        findType = findType.cycle(true);
                    } else {
                        findType = findType.cycle(false);
                    }
                    button.setDisplayString(findType.getDisplayName());
                    searchText(search.getValue());
                }));
        send = new TextFieldRunnable(
                2,
                height - 15,
                width - 4,
                12,
                font,
                (textFieldRunnable -> {
                    if (minecraft.player != null) {
                        minecraft.player.sendSystemMessage(Component.literal(textFieldRunnable.getValue()));
                    }
                    textFieldRunnable.setValue("");
                })
        );
        addTextField(send, null);
        send.setFocused(true);
    }

    @Override
    public boolean onMouseClicked(MouseButtonEvent click, boolean doubled) {
        if (super.onMouseClicked(click, doubled)) {
            return true;
        }
        if (click.button() == 1) {
            createContextMenu((int) click.x(), (int) click.y());
            return true;
        }
        if (menu != null && menu.onMouseClicked(click, doubled)) {
            return true;
        }
        if (click.hasShiftDown()) {
            relativeScroll((int) click.y());
            return true;
        }
        Style style = getHoverStyle(click.x(), click.y());
        if (style != null && style.getClickEvent() != null) {
            ScreenInvoker.advancedchatlog$handleClickEvent(style.getClickEvent(), minecraft, this);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        if (super.mouseDragged(click, deltaX, deltaY)) {
            return true;
        }
        if (click.hasShiftDown()) {
            relativeScroll((int) click.y());
            return true;
        }
        return false;
    }

    public void relativeScroll(int y) {
        // Scroll click
        int height = minecraft.getWindow().getGuiScaledHeight() - 100;
        y -= 40;
        float percent = 1 - Math.max(0, Math.min((float) y / height, 1));
        int newPix = (int) (percent * (renderLines.size() * (font.lineHeight + 2)));
        scrollEnd = newPix;
        scrollStart = newPix;
        lastScrollTime = Util.getMillis();
    }


    private void searchText(String contents) {
        if (contents.isEmpty()) {
            setLines(ChatLogData.getInstance().getMessages());
            return;
        }
        List<LogChatMessage> sorted = new ArrayList<>();
        for (LogChatMessage l : ChatLogData.getInstance().getMessages()) {
            ChatMessage m = l.getMessage();
            try {
                if (SearchUtils.isMatch(m.getDisplayText().getString(), contents, findType)) {
                    sorted.add(l);
                }
            } catch (PatternSyntaxException e) {
                sorted.clear();
                Component text = Component.literal(
                        StringUtils.translate("advancedchatlog.message.regexerror")).withStyle(
                        Style.EMPTY.withColor(TextColor.fromLegacyFormat(ChatFormatting.RED)));
                text.getSiblings().add(Component.literal(" " + e.getDescription()).withStyle(Style.EMPTY.withColor(Colors.getInstance().getColorOrWhite("gray").color())));
                ChatMessage message = ChatMessage.builder().displayText(text).originalText(text).build();
                sorted.add(new LogChatMessage(message));
                break;
            }
        }
        setLines(sorted);
    }

    private void setLines(List<LogChatMessage> messages) {
        // Don't want jank
        messages = new ArrayList<>(messages);
        if (messages.isEmpty()) {
            Component text = Component.literal(
                    StringUtils.translate("advancedchatlog.message.none")
            ).withStyle(Style.EMPTY.withColor(TextColor.fromLegacyFormat(ChatFormatting.RED)));
            messages.add(new LogChatMessage(ChatMessage.builder().displayText(text).originalText(text).build()));
        }
        renderLines = new ArrayList<>();
        for (LogChatMessage l : messages) {
            ChatMessage m = l.getMessage();
            for (int i = m.getLineCount() - 1; i >= 0; i--) {
                renderLines.add(m.getLines().get(i));
            }
        }
    }

    private void updateScroll() {
        long time = Util.getMillis();
        // Starting scroll + percent completed
        currentScroll = scrollStart + (
                (scrollEnd - scrollStart) * (1 - ((ConfigStorage.Easing) ChatLogConfigStorage.General.SCROLL_TYPE.config.getOptionListValue()).apply(
                        1 - ((float) time - lastScrollTime) / ChatLogConfigStorage.General.SCROLL_TIME.config.getIntegerValue()
                ))
        );
        int lineHeight = (font.lineHeight + 2);
        if (currentScroll < 0) {
            // Make sure we can still see at least one line
            currentScroll = 0;
            scrollEnd = 0;
            lastScrollTime = 0;
        }
        int maxY = fontHeight * (renderLines.size() - 1);
        if (currentScroll >= maxY) {
            // Make sure it stops at the top
            currentScroll = maxY;
            scrollEnd = maxY;
            lastScrollTime = 0;
        }
    }

    @Override
    public boolean onMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (super.onMouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return true;
        }
        // Update the scroll variables
        scrollEnd = currentScroll + verticalAmount * 10 * ChatLogConfigStorage.General.SCROLL_MULTIPLIER.config.getDoubleValue();
        scrollStart = currentScroll;
        lastScrollTime = Util.getMillis();
        return true;

    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTicks) {
        super.extractRenderState(context, mouseX, mouseY, partialTicks);
        updateScroll();
        int height = minecraft.getWindow().getGuiScaledHeight();
        int width = minecraft.getWindow().getGuiScaledWidth();
        int lineHeight = font.lineHeight + 2;
        // 60 px top, 40 px bottom
        int lines = (int) Math.ceil((float) (height - 70 - lineHeight) / (lineHeight));

        // Current line scrolled
        int scrollLine = (int) Math.floor((float) currentScroll / (lineHeight));

        // Offset y for scrolling. Used for partially obstructed lines.
        int y = -1 * ((int) currentScroll % lineHeight);

        // Scissor to keep boundaries for the half scroll
        double scale = minecraft.getWindow().getGuiScale();
        context.enableScissor(
                0, (int) (40 * scale), (int) (width * scale), (int) ((height - 70) * scale));

        for (int i = scrollLine; i < scrollLine + lines; i++) {
            if (i >= renderLines.size()) {
                break;
            }
            ChatMessage.AdvancedChatLine line = renderLines.get(i);
            context.text(
                    font,
                    line.getText(),
                    10,
                    height - y - 40 - fontHeight,
                    Colors.getInstance().getColorOrWhite("white").color());
            y += lineHeight;
        }
        context.disableScissor();
        context.centeredText(
                font,
                (scrollLine + 1) + "/" + renderLines.size(),
                width / 2,
                height - 28,
                Colors.getInstance().getColorOrWhite("white").color()
        );
        renderHoverText(context, getHoverStyle(mouseX, mouseY), mouseX, mouseY);
        if (menu != null) {
            menu.render(GuiContext.fromGuiGraphics(context), mouseX, mouseY, true);
        }
    }

    /**
     * 26.2 removed GuiGraphics#renderComponentHoverEffect in favour of the text-collector
     * pipeline. Only SHOW_TEXT hovers were ever meaningful here, so draw those directly
     * as a tooltip instead.
     */
    private void renderHoverText(
            GuiGraphicsExtractor context, Style style, int mouseX, int mouseY) {
        if (style == null) {
            return;
        }
        if (style.getHoverEvent() instanceof HoverEvent.ShowText showText) {
            context.setTooltipForNextFrame(font, showText.value(), mouseX, mouseY);
        }
    }

    public void createContextMenu(int mouseX, int mouseY) {
        LinkedHashMap<Component, ContextMenu.ContextConsumer> actions = new LinkedHashMap<>();
        message = getMessage(mouseX, mouseY);
        if (message != null) {
            Component data = Component.empty();
            try {
                data.getSiblings().add(
                        Component.literal(
                                message.getMessage().getTime().format(DateTimeFormatter.ofPattern(ConfigStorage.General.TIME_FORMAT.config.getStringValue()))
                                ).withStyle(Style.EMPTY.applyFormat(ChatFormatting.AQUA))
                    );
            } catch (IllegalArgumentException e) {
                AdvancedChatLog.LOGGER.log(Level.WARN, "Can't format time for context menu!", e);
            }
            if (message.getMessage().getOwner() != null) {
                data.getSiblings().add(Component.literal(" - ").withStyle(Style.EMPTY.applyFormat(ChatFormatting.GRAY)));
                if (message.getMessage().getOwner().getEntry().getTabListDisplayName() != null) {
                    data.getSiblings().add(message.getMessage().getOwner().getEntry().getTabListDisplayName());
                } else {
                    data.getSiblings().add(Component.literal(message.getMessage().getOwner().getEntry().getProfile().name()));
                }
            }
            if (!data.getString().isBlank())  {
                actions.put(data, (x, y) -> {
                });
            }
            actions.put(Component.literal(StringUtils.translate("advancedchatlog.context.copy")), (x, y) -> {
                Minecraft.getInstance().keyboardHandler.setClipboard(message.getMessage().getOriginalText().getString());
                InfoUtils.printActionbarMessage("advancedchatlog.context.copied");
            });
        }
        actions.put(Component.literal(StringUtils.translate("advancedchatlog.context.clearallmessages")), (x, y) -> {
            ChatLogData.getInstance().clear();
            setLines(ChatLogData.getInstance().getMessages());
        });
        menu = new ContextMenu(mouseX, mouseY, actions, () -> menu = null);
    }

    public Style getHoverStyle(double mouseX, double mouseY) {
        int lineHeight = font.lineHeight + 2;
        int lines = (int) Math.ceil((float) (height - 70 - lineHeight) / (lineHeight));

        // Current line scrolled
        int scrollLine = (int) Math.floor((float) currentScroll / (lineHeight));

        // Offset y for scrolling. Used for partially obstructed lines.
        int y = -1 * ((int) currentScroll % lineHeight);
        int height = minecraft.getWindow().getGuiScaledHeight();
        // Change the perspective of mouseY from where the text started.
        mouseY = height - mouseY - 40;
        mouseX = mouseX - 10;

        for (int i = scrollLine; i < scrollLine + lines; i++) {
            if (i >= renderLines.size()) {
                break;
            }
            if (y <= mouseY && y + lineHeight >= mouseY) {
                ChatMessage.AdvancedChatLine line = renderLines.get(i);
                return getStyleAt(line.getText(), (int) mouseX);
            }
            y += lineHeight;
        }
        return null;
    }

    private Style getStyleAt(Component text, int x) {
        final float[] width = {0};
        final Style[] found = {null};
        text.visit(
                (style, string) -> {
                    int[] codePoints = string.codePoints().toArray();
                    for (int codePoint : codePoints) {
                        float glyphWidth =
                                this.minecraft.font.width(
                                        String.valueOf(Character.toChars(codePoint)));
                        if (x < width[0] + glyphWidth) {
                            found[0] = style;
                            return java.util.Optional.of(style);
                        }
                        width[0] += glyphWidth;
                    }
                    return java.util.Optional.empty();
                },
                Style.EMPTY);
        return found[0];
    }
    public LogChatMessage getMessage(double mouseX, double mouseY) {
        int lineHeight = font.lineHeight + 2;
        int lines = (int) Math.ceil((float) (height - 70 - lineHeight) / (lineHeight));

        // Current line scrolled
        int scrollLine = (int) Math.floor((float) currentScroll / (lineHeight));

        // Offset y for scrolling. Used for partially obstructed lines.
        int y = -1 * ((int) currentScroll % lineHeight);
        int height = minecraft.getWindow().getGuiScaledHeight();
        // Change the perspective of mouseY from where the text started.
        mouseY = height - mouseY - 40;

        for (int i = scrollLine; i < scrollLine + lines; i++) {
            if (i >= renderLines.size()) {
                break;
            }
            if (y <= mouseY && y + lineHeight >= mouseY) {
                ChatMessage.AdvancedChatLine line = renderLines.get(i);
                return ChatLogData.getInstance().getLogMessage(line.getParent());
            }
            y += lineHeight;
        }
        return null;
    }


}
