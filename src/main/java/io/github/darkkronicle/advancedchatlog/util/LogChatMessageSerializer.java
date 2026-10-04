/*
 * Copyright (C) 2021-2025 DarkKronicle
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.github.darkkronicle.advancedchatlog.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.serialization.JsonOps;
import io.github.darkkronicle.advancedchatcore.chat.ChatMessage;
import io.github.darkkronicle.advancedchatcore.interfaces.IJsonSave;
import io.github.darkkronicle.advancedchatlog.AdvancedChatLog;
import io.github.darkkronicle.advancedchatlog.config.ChatLogConfigStorage;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.util.StrictJsonParser;

@Environment(EnvType.CLIENT)
public class LogChatMessageSerializer implements IJsonSave<LogChatMessage> {
    private final DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public LogChatMessageSerializer() {}

    private Style cleanStyle(Style style) {
        if (!ChatLogConfigStorage.General.CLEAN_SAVE.config.getBooleanValue()) {
            return style;
        }
        style = style.withClickEvent(null);
        style = style.withHoverEvent(null);
        style = style.withInsertion(null);
        return style;
    }

    private Component transfer(Component text) {
        // Using the built in serializer LiteralText is required
        Component base = Component.empty();
        for (Component t : text.getSiblings()) {
            Component newT = Component.literal(t.getString()).withStyle(cleanStyle(t.getStyle()));
            base.getSiblings().add(newT);
        }
        return base;
    }

    private Style forceCleanStyle(Style style) {
        style = style.withClickEvent(null);
        style = style.withHoverEvent(null);
        style = style.withInsertion(null);
        return style;
    }

    private Component forceTransfer(Component text) {
        // Using the built in serializer LiteralText is required
        Component base = Component.empty();
        for (Component t : text.getSiblings()) {
            Component newT = Component.literal(t.getString()).withStyle(forceCleanStyle(t.getStyle()));
            base.getSiblings().add(newT);
        }
        return base;
    }

    @Override
    public LogChatMessage load(JsonObject obj) {
        LocalDateTime dateTime = LocalDateTime.from(formatter.parse(obj.get("time").getAsString()));
        LocalDate date = dateTime.toLocalDate();
        LocalTime time = dateTime.toLocalTime();
        Component display = ComponentSerialization.CODEC.parse(
                RegistryAccess.EMPTY.createSerializationContext(JsonOps.INSTANCE),
                obj.get("display")).resultOrPartial((string2) -> {}).orElse(null);
        Component original = ComponentSerialization.CODEC.parse(
                RegistryAccess.EMPTY.createSerializationContext(JsonOps.INSTANCE),
                obj.get("original")).resultOrPartial((string2) -> {}).orElse(null);
        int stacks = obj.get("stacks").getAsByte();
        ChatMessage message =
                ChatMessage.builder()
                        .time(time)
                        .displayText(display)
                        .originalText(original)
                        .build();
        return new LogChatMessage(message, date);
    }

    @Override
    public JsonObject save(LogChatMessage message) {
        JsonObject json = new JsonObject();
        ChatMessage chat = message.getMessage();
        LocalDateTime dateTime = LocalDateTime.of(message.getDate(), chat.getTime());
        try {
            json.addProperty("time", formatter.format(dateTime));
            json.addProperty("stacks", chat.getStacks());
            json.add("display", ComponentSerialization.CODEC.encodeStart(
                    RegistryAccess.EMPTY.createSerializationContext(JsonOps.INSTANCE),
                    transfer(chat.getDisplayText())).getOrThrow());
            json.add("original", ComponentSerialization.CODEC.encodeStart(
                    RegistryAccess.EMPTY.createSerializationContext(JsonOps.INSTANCE),
                    transfer(chat.getOriginalText())).getOrThrow());
        } catch (JsonParseException e) {
            try {
                AdvancedChatLog.LOGGER.warn("[AdvancedChatLog] Save Error 1: {}", e.getMessage());
                AdvancedChatLog.LOGGER.warn("Original Message:");
                AdvancedChatLog.LOGGER.warn(chat.getOriginalText().getString());
                AdvancedChatLog.LOGGER.warn(chat.getOriginalText().toString());
                json = new JsonObject();
                json.addProperty("time", formatter.format(dateTime));
                json.addProperty("stacks", chat.getStacks());
                json.add("display", ComponentSerialization.CODEC.encodeStart(
                        RegistryAccess.EMPTY.createSerializationContext(JsonOps.INSTANCE),
                        forceTransfer(chat.getDisplayText())).getOrThrow());
                json.add("original", ComponentSerialization.CODEC.encodeStart(
                        RegistryAccess.EMPTY.createSerializationContext(JsonOps.INSTANCE),
                        forceTransfer(chat.getOriginalText())).getOrThrow());
            } catch (Exception e2) {
                AdvancedChatLog.LOGGER.warn("[AdvancedChatLog] Save Error 2", e2);
                return new JsonObject();
            }
        }
        return json;
    }
}
