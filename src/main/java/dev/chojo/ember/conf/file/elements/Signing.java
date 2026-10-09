/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.conf.file.elements;

import dev.chojo.ocular.override.Env;
import dev.chojo.ocular.override.Overwrite;
import dev.chojo.ocular.override.OverwritePrefix;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration for the seals Ember puts on the documents it signs and issues.
 */
@SuppressWarnings({"FieldCanBeLocal", "FieldMayBeFinal", "CanBeFinal"})
@OverwritePrefix("SIGNING")
public class Signing {

    /**
     * Whether every seal asks a timestamp service for a timestamp.
     *
     * <p>A timestamp proves the seal existed at that moment, so nobody, the operator included, can
     * date a seal back later. Only a hash of the sealed document leaves the installation, never the
     * document. Ember also fetches the timestamp services' public revocation lists and asks their
     * status responders (OCSP) about their certificates, which sends nothing but a certificate's
     * serial number and carries no data from the document. When no service answers, the seal is made
     * without a timestamp instead of failing; for a signed document, an hourly job adds the timestamp once a
     * service answers again, filed as a new version of the document.
     * Switched off, nothing leaves the installation and the time of a seal is only this server's
     * clock, which is the right setting for an installation without outbound access.
     */
    @Overwrite(env = @Env)
    private boolean timestamps = true;

    /**
     * The RFC 3161 timestamp services, asked in this order until one answers.
     *
     * <p>Public services that are free and need no account. Not all of them are on Adobe's list of
     * trusted services (FreeTSA is not), so a seal stamped by a later one may show its timestamp as
     * unverified in Adobe Reader, while the timestamp still proves the time. Each service only ever
     * receives a hash. An empty list switches timestamps off just like {@link #timestamps} does.
     *
     * <p>A timestamp counts only when it chains to the root certificate pinned for the service that
     * gave it; otherwise the next service is asked. Ember ships the root of every default service. A
     * service added here needs its root in {@link #timestampRoots}, or it is never asked.
     */
    @Overwrite(env = @Env)
    private List<String> timestampUrls = List.of(
            "http://timestamp.digicert.com",
            "http://timestamp.sectigo.com",
            "http://timestamp.globalsign.com/tsa/r6advanced1",
            "http://time.certum.pl",
            "http://timestamp.acs.microsoft.com",
            "http://timestamp.apple.com/ts01",
            "https://freetsa.org/tsr");

    /**
     * Root certificates for timestamp services, by the service's address as it is spelled in
     * {@link #timestampUrls}, each the path of a PEM or DER certificate file, for example
     * {@code "https://tsa.example.org/tsr": /etc/ember/tsa-root.pem}. A timestamp of that service
     * counts only when its signing certificate chains to this root. Needed for every service added to
     * the list; an entry for a default service replaces the root Ember ships for it.
     */
    private Map<String, String> timestampRoots = Collections.emptyMap();

    /**
     * Whether Ember renews the timestamps of sealed documents before they run out (PAdES {@code BASELINE-LTA}).
     *
     * <p>A timestamp proves the time of a seal only while the certificates of its timestamp service are
     * valid, usually about ten years. Switched on, a daily job looks for sealed documents whose newest
     * timestamp rests on a certificate that ends within half a year, adds the current validation material
     * and a new timestamp over the whole document, and files the result as a new version beside the old
     * one. Each renewal sends one more hash to a timestamp service. It is an operational duty that only
     * helps while it keeps running for as long as the documents are kept, which is why it is off by default.
     * It needs {@link #timestamps}; without them nothing is renewed.
     */
    @Overwrite(env = @Env)
    private boolean archiveTimestamps = false;

    public boolean timestamps() {
        return timestamps;
    }

    /** @return whether the timestamps of sealed documents are renewed before they run out */
    public boolean archiveTimestamps() {
        return archiveTimestamps;
    }

    /** @return the configured services in order, blank entries left out */
    public List<String> timestampUrls() {
        return Objects.requireNonNullElse(timestampUrls, List.<String>of()).stream()
                .map(String::strip)
                .filter(url -> !url.isEmpty())
                .toList();
    }

    /** @return the operator's root certificate files by service address, addresses stripped */
    public Map<String, String> timestampRoots() {
        var roots = new LinkedHashMap<String, String>();
        Objects.requireNonNullElse(timestampRoots, Map.<String, String>of()).forEach((url, file) -> {
            if (url != null && file != null) roots.put(url.strip(), file);
        });
        return Collections.unmodifiableMap(roots);
    }

    @Override
    public String toString() {
        return "Signing{timestamps=" + timestamps + ", timestampUrls=" + timestampUrls + ", timestampRoots="
                + timestampRoots + ", archiveTimestamps=" + archiveTimestamps + '}';
    }
}
