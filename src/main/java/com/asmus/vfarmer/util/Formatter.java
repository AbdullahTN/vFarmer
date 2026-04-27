package com.asmus.vfarmer.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Number formatting utilities.
 */
public class Formatter {

    private static final DecimalFormat MONEY_FORMAT;
    private static final DecimalFormat COMPACT_FORMAT;
    private static final DecimalFormat PERCENT_FORMAT;
    private static final DecimalFormat DECIMAL_FORMAT;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator(',');
        symbols.setDecimalSeparator('.');

        MONEY_FORMAT = new DecimalFormat("#,##0.00", symbols);
        COMPACT_FORMAT = new DecimalFormat("#,##0.##", symbols);
        PERCENT_FORMAT = new DecimalFormat("#0.0", symbols);
        DECIMAL_FORMAT = new DecimalFormat("#,##0", symbols);
    }

    /** Format as money: 1,234.56 */
    public static String formatMoney(double value) {
        return MONEY_FORMAT.format(value);
    }

    /** Format compact: 1,234.5 */
    public static String formatCompact(double value) {
        return COMPACT_FORMAT.format(value);
    }

    /** Format as integer with grouping: 1,234 */
    public static String formatInteger(double value) {
        return DECIMAL_FORMAT.format(value);
    }

    /** Format as percentage: 45.2 */
    public static String formatPercent(double value) {
        return PERCENT_FORMAT.format(value);
    }

    /** Format with suffix: 1.2K, 3.4M etc */
    public static String formatSuffix(double value) {
        if (value < 1_000) return formatCompact(value);
        if (value < 1_000_000) return formatCompact(value / 1_000) + "K";
        if (value < 1_000_000_000) return formatCompact(value / 1_000_000) + "M";
        return formatCompact(value / 1_000_000_000) + "B";
    }
}
