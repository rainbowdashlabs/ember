/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.conf.file.elements;

import dev.chojo.ocular.override.Env;
import dev.chojo.ocular.override.Overwrite;
import dev.chojo.ocular.override.OverwritePrefix;

import java.util.List;
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
     * document. When no service answers, the seal is made without a timestamp instead of failing.
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

    public boolean timestamps() {
        return timestamps;
    }

    /** @return the configured services in order, blank entries left out */
    public List<String> timestampUrls() {
        return Objects.requireNonNullElse(timestampUrls, List.<String>of()).stream()
                .map(String::strip)
                .filter(url -> !url.isEmpty())
                .toList();
    }

    @Override
    public String toString() {
        return "Signing{timestamps=" + timestamps + ", timestampUrls=" + timestampUrls + '}';
    }
}
