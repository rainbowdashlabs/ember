/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.maps.entity;

import org.jspecify.annotations.Nullable;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * One address search: the text, the country it is limited to and, where known, a point the
 * search prefers matches near.
 *
 * @param text        what is searched for
 * @param countryCode the two-letter country code the search is limited to, or null for anywhere
 * @param biasLatitude  latitude of the point near which matches are preferred, or null
 * @param biasLongitude longitude of that point, or null
 */
public record GeocodeQuery(
        String text,
        @Nullable String countryCode,
        @Nullable Double biasLatitude,
        @Nullable Double biasLongitude) {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    /**
     * A search for a postal address, written the way Photon reads it best:
     * {@code <line>, <postal code> <city>}. Blank parts are left out.
     *
     * @param addressLine street and house number
     * @param postalCode  postal code
     * @param city        town
     * @param countryCode two-letter country code, or null
     * @return the search
     */
    public static GeocodeQuery address(
            @Nullable String addressLine,
            @Nullable String postalCode,
            @Nullable String city,
            @Nullable String countryCode) {
        String town = joined(" ", postalCode, city);
        return new GeocodeQuery(joined(", ", addressLine, town), countryCode, null, null);
    }

    /**
     * The text as it is compared: trimmed, in lower case, every run of whitespace made one space.
     * Whitespace means every Unicode space, a no-break space included, the same as the browser's.
     *
     * @param text the text as typed
     * @return its normal form
     */
    public static String normalise(String text) {
        return WHITESPACE.matcher(text).replaceAll(" ").strip().toLowerCase(Locale.ROOT);
    }

    private static String joined(String separator, @Nullable String... parts) {
        return Stream.of(parts)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .collect(Collectors.joining(separator));
    }

    /**
     * Whether there is anything to search for.
     *
     * @return true when the text holds nothing but whitespace
     */
    public boolean isBlank() {
        return normalise(text).isEmpty();
    }

    /**
     * The same search under its normal form, so two spellings of one search are one cache entry.
     *
     * @return a key naming this search
     */
    public String cacheKey() {
        return String.join(
                "|",
                normalise(text),
                Objects.requireNonNullElse(countryCode, "").toUpperCase(Locale.ROOT),
                Objects.toString(biasLatitude, ""),
                Objects.toString(biasLongitude, ""));
    }

    /**
     * The query part of a Photon search for this query, asking for the single best match in German.
     *
     * @return the encoded query string, without the leading question mark
     */
    public String photonParameters() {
        var parameters = new StringBuilder("q=").append(encode(text)).append("&limit=1&lang=de");
        if (countryCode != null && !countryCode.isBlank()) {
            parameters.append("&countrycode=").append(encode(countryCode.trim().toUpperCase(Locale.ROOT)));
        }
        if (biasLatitude != null && biasLongitude != null) {
            parameters
                    .append("&lat=")
                    .append(String.format(Locale.ROOT, "%.6f", biasLatitude))
                    .append("&lon=")
                    .append(String.format(Locale.ROOT, "%.6f", biasLongitude));
        }
        return parameters.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
