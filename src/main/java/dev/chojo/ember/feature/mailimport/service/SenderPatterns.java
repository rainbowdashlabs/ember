/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mailimport.service;

import java.util.List;
import java.util.Locale;

/**
 * Which senders a rule trusts.
 *
 * <p>Two forms and no more: one address, or every address at one domain written {@code *@domain}.
 * Compared in lower case, the first as an exact match and the second as an exact match on the domain.
 * Subdomains are not implied, so mail from one needs its own pattern.
 *
 * <p>There is deliberately no general pattern language here. A language wide enough to write
 * {@code .*} in is a language somebody eventually writes {@code .*} in, and this is the check that
 * decides whether a stranger can put files into a station's document store.
 */
public final class SenderPatterns {
    private static final String DOMAIN_PREFIX = "*@";
    private static final char AT = '@';

    private SenderPatterns() {}

    /**
     * Whether any of these patterns accepts this sender.
     *
     * <p>An empty list accepts nobody. The empty case has to fail closed: the natural reading of an
     * empty filter is "everything", and here that would be an open door left open by saying nothing.
     *
     * @param patterns the rule's patterns, in any order
     * @param sender   the address the mail came from, already reduced to the bare address
     * @return whether the mail may be taken
     */
    public static boolean accepts(List<String> patterns, String sender) {
        if (patterns == null || patterns.isEmpty()) return false;
        if (sender == null || sender.isBlank()) return false;
        String address = sender.trim().toLowerCase(Locale.ROOT);
        String domain = domainOf(address);
        for (String pattern : patterns) {
            if (pattern == null || pattern.isBlank()) continue;
            String candidate = pattern.trim().toLowerCase(Locale.ROOT);
            if (candidate.startsWith(DOMAIN_PREFIX)) {
                if (domain != null && domain.equals(candidate.substring(DOMAIN_PREFIX.length()))) return true;
                continue;
            }
            if (candidate.equals(address)) return true;
        }
        return false;
    }

    /**
     * Whether this is one of the two forms, so the page can refuse the rest as it is typed rather than
     * storing a pattern that will never match anything.
     *
     * @param pattern the pattern as somebody wrote it
     * @return whether it is an address or a domain wildcard
     */
    public static boolean isValid(String pattern) {
        if (pattern == null || pattern.isBlank()) return false;
        String candidate = pattern.trim().toLowerCase(Locale.ROOT);
        if (candidate.indexOf(' ') >= 0) return false;
        if (candidate.startsWith(DOMAIN_PREFIX)) {
            return isDomain(candidate.substring(DOMAIN_PREFIX.length()));
        }
        int at = candidate.indexOf(AT);
        if (at <= 0 || at != candidate.lastIndexOf(AT)) return false;
        if (candidate.indexOf('*') >= 0) return false;
        return isDomain(candidate.substring(at + 1));
    }

    private static boolean isDomain(String domain) {
        if (domain.isBlank() || domain.indexOf('*') >= 0) return false;
        if (domain.startsWith(".") || domain.endsWith(".")) return false;
        return domain.indexOf('.') > 0;
    }

    private static String domainOf(String address) {
        int at = address.lastIndexOf(AT);
        if (at < 0 || at == address.length() - 1) return null;
        return address.substring(at + 1);
    }
}
