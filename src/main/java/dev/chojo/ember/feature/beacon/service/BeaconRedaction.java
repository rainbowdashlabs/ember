/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.beacon.service;

import dev.chojo.ember.feature.account.repository.AccountRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a text about a fault may say once it leaves the instance for a beacon: the fault, not the
 * people it happened to.
 *
 * <p>Secrets written as a key and a value ({@code password: ...}, {@code token=...}, {@code code=...})
 * lose the value to {@code [secret]}, mail addresses become {@code [mail]}, IP addresses {@code [ip]},
 * identifiers in UUID form {@code [uuid]}, and every word of a person's first name, last name or user
 * name that is at least three letters long {@code [name]}, matched as a whole word and regardless of
 * case. Everything else stays as it was written: a path, a number, a status code, which are what make
 * a fault findable again.
 *
 * <p>It catches what can be recognised, not everything a line could say about somebody: a name shorter
 * than three letters, a name written together with another word, or the name of somebody this
 * instance holds no account or name change for stays as it was.
 *
 * <p>The names are read once per delivery through {@link #redactor()}, not once per line, so a fault
 * with sixty lines asks the database once.
 */
@Singleton
public class BeaconRedaction {

    /** What may not stand in a mail address: whitespace and what usually surrounds one. */
    private static final String NOT_IN_MAIL = "[^\\s@<>()\\[\\]\"',;:]";

    /** A mail address, internationalised ones included, which is a person and never a diagnosis. */
    static final Pattern MAIL_ADDRESS =
            Pattern.compile(NOT_IN_MAIL + "+@" + NOT_IN_MAIL + "+\\." + "[^\\s@<>()\\[\\]\"',;:.]+");

    /**
     * The value of a key that names a secret, written with an equals sign or a colon, a bearer token
     * taken whole. A {@code code} counts only as a parameter ({@code code=...}), so a status code or a
     * refusal code written with a colon stays readable.
     */
    static final Pattern SECRET =
            Pattern.compile("(?i)\\b(password|passwort|passphrase|secret|token|otp|authorization|cookie|api[_-]?key"
                    + "|code(?=\\s*=))(\\s*[=:]\\s*)(?:bearer\\s+)?[^\\s&,;\"']+");

    /** An IPv4 address. */
    static final Pattern IPV4 = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b");

    /**
     * Something shaped like an IPv6 address. Only a match with a {@code ::}, a hex letter or more than
     * three groups is taken for one, so a clock reading such as 10:00:00 stays.
     */
    static final Pattern IPV6 = Pattern.compile("(?i)(?<![\\w:])(?:[0-9a-f]{0,4}:){2,7}[0-9a-f]{1,4}(?![\\w:])");

    /** An identifier in UUID form, which names a record of somebody here and means nothing to a beacon. */
    static final Pattern UUID = Pattern.compile(
            "\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b", Pattern.CASE_INSENSITIVE);

    /** A word, as names are split into words. */
    private static final Pattern WORD = Pattern.compile("\\p{L}+");

    /** The words the replacements are written with, which no name may replace in turn. */
    private static final Set<String> PLACEHOLDERS = Set.of("secret", "mail", "ip", "uuid", "name");

    private final AccountRepository accounts;

    @Inject
    public BeaconRedaction(AccountRepository accounts) {
        this.accounts = accounts;
    }

    /**
     * A redactor knowing the names of this instance's people as they are now: its accounts, and the
     * names asked for but not yet given.
     *
     * @return the redactor, to use for one delivery
     */
    public Redactor redactor() {
        return new Redactor(accounts.nameWords());
    }

    private static boolean isIpv6(String candidate) {
        return candidate.contains("::")
                || candidate.chars().anyMatch(c -> Character.isLetter(c))
                || candidate.chars().filter(c -> c == ':').count() > 3;
    }

    /** Redacts texts against one reading of the names. */
    public static final class Redactor {
        private final Set<String> names;

        /**
         * @param names the words to replace, lower case
         */
        public Redactor(Set<String> names) {
            this.names = names;
        }

        /**
         * The text with secrets, mail addresses, IP addresses, identifiers and names replaced.
         *
         * @param text the text, or null
         * @return the redacted text, or null where none was given
         */
        public @Nullable String redact(@Nullable String text) {
            if (text == null || text.isEmpty()) return text;
            String result = SECRET.matcher(text).replaceAll("$1$2[secret]");
            result = MAIL_ADDRESS.matcher(result).replaceAll("[mail]");
            result = UUID.matcher(result).replaceAll("[uuid]");
            result = IPV4.matcher(result).replaceAll("[ip]");
            result = IPV6.matcher(result)
                    .replaceAll(match -> isIpv6(match.group()) ? "[ip]" : Matcher.quoteReplacement(match.group()));
            if (names.isEmpty()) return result;
            return WORD.matcher(result).replaceAll(word -> {
                String lower = word.group().toLowerCase(Locale.ROOT);
                return names.contains(lower) && !PLACEHOLDERS.contains(lower)
                        ? "[name]"
                        : Matcher.quoteReplacement(word.group());
            });
        }
    }
}
