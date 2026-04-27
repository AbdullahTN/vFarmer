package com.asmus.vfarmer.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for Minecraft color code handling (hex &#RRGGBB and legacy &x).
 */
public class ColorUtil {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    /**
     * Converts hex color codes (&#RRGGBB) and legacy (&c, &l etc) to section char format.
     */
    public static String colorize(String text) {
        if (text == null) return "";
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) {
                replacement.append('§').append(c);
            }
            matcher.appendReplacement(sb, replacement.toString());
        }
        matcher.appendTail(sb);
        return sb.toString().replace('&', '§');
    }

    /**
     * Converts a string with color codes to an Adventure Component.
     */
    public static Component toComponent(String text) {
        return LegacyComponentSerializer.legacySection().deserialize(colorize(text));
    }

    /**
     * Strips all color codes from a string.
     */
    public static String stripColor(String text) {
        if (text == null) return "";
        return text.replaceAll("§[0-9a-fk-orx]", "").replaceAll("&#[A-Fa-f0-9]{6}", "").replaceAll("&[0-9a-fk-or]", "");
    }

    /**
     * Converts hex & color codes to legacy §-based string (for titles, etc).
     */
    public static String toLegacy(String text) {
        if (text == null) return "";
        // Convert &#RRGGBB to §x§R§R§G§G§B§B
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("&#([A-Fa-f0-9]{6})").matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) {
                replacement.append("§").append(c);
            }
            matcher.appendReplacement(sb, replacement.toString());
        }
        matcher.appendTail(sb);
        return sb.toString().replace("&", "§");
    }
}
