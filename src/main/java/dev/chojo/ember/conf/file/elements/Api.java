/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.conf.file.elements;

public class Api {
    private String host = "0.0.0.0";
    private int port = 8080;
    private String baseUrl = "http://localhost:5173";

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    public String baseUrl() {
        return baseUrl;
    }
}
