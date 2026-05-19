/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.conf.file.elements;

@SuppressWarnings({"FieldCanBeLocal", "FieldMayBeFinal"})
public class Api {
    private String host = "0.0.0.0";
    private int port = 8080;
    private String baseUrl = "http://localhost:5173";
    private String demoUrl = "";
    private int maxAvatarSizeBytes = 2 * 1024 * 1024;
    private String privacyPolicyDir = "data/privacy";
    private String consentDir = "data/consent";
    private String tosDir = "data/tos";
    private String imprintDir = "data/imprint";

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String demoUrl() {
        return demoUrl;
    }

    public int maxAvatarSizeBytes() {
        return maxAvatarSizeBytes;
    }

    public String privacyPolicyDir() {
        return privacyPolicyDir;
    }

    public String consentDir() {
        return consentDir;
    }

    public String tosDir() {
        return tosDir;
    }

    public String imprintDir() {
        return imprintDir;
    }

    @Override
    public String toString() {
        return "Api{" + "host='" + host + '\'' + ", port=" + port + ", baseUrl='" + baseUrl + '\'' + ", demoUrl='"
                + demoUrl + '\'' + '}';
    }
}
