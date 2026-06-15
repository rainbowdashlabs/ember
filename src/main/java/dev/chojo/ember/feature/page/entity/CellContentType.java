/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.page.entity;

public enum CellContentType {
    EMPTY(CellConfig.MarkdownConfig.class, new CellConfig.MarkdownConfig()),
    MARKDOWN(CellConfig.MarkdownConfig.class, new CellConfig.MarkdownConfig()),
    IMAGE(CellConfig.ImageConfig.class, new CellConfig.ImageConfig(null, null, null, null)),
    VIDEO(CellConfig.VideoConfig.class, new CellConfig.VideoConfig(null, null));

    private final Class<? extends CellConfig> configClass;
    private final CellConfig emptyConfig;

    CellContentType(Class<? extends CellConfig> configClass, CellConfig emptyConfig) {
        this.configClass = configClass;
        this.emptyConfig = emptyConfig;
    }

    public Class<? extends CellConfig> configClass() {
        return configClass;
    }

    public CellConfig emptyConfig() {
        return emptyConfig;
    }
}
