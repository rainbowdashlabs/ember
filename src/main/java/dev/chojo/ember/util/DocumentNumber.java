/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import java.util.Locale;

/**
 * A number the way an exported document prints it: a whole number without decimals, anything else
 * to one decimal in the reader's locale, so a German sheet reads {@code 2,5} where an English one
 * reads {@code 2.5}.
 */
public final class DocumentNumber {
    private DocumentNumber() {}

    public static String of(double value, Locale locale) {
        if (value == Math.rint(value) && !Double.isInfinite(value)) return String.valueOf((long) value);
        return String.format(locale, "%.1f", value);
    }
}
