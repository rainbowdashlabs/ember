/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.conf.file.elements;

import dev.chojo.ember.util.SizeParser;
import dev.chojo.ocular.override.Env;
import dev.chojo.ocular.override.Overwrite;
import dev.chojo.ocular.override.OverwritePrefix;

@SuppressWarnings({"FieldCanBeLocal", "FieldMayBeFinal", "CanBeFinal"})
@OverwritePrefix("STORAGE")
public class Storage {
    @Overwrite(env = @Env)
    private String defaultTotal = "5G";

    @Overwrite(env = @Env)
    private String defaultKb = "4G";

    @Overwrite(env = @Env)
    private String defaultBoard = "3G";

    @Overwrite(env = @Env)
    private String defaultImages = "1G";

    @Overwrite(env = @Env)
    private String defaultPages = "1G";

    @Overwrite(env = @Env)
    private String defaultPerFile = "50M";

    @Overwrite(env = @Env)
    private String defaultPerImage = "5M";

    @Overwrite(env = @Env)
    private int warningThresholdPercent = 80;

    @Overwrite(env = @Env)
    private boolean compressPresentations = true;

    @Overwrite(env = @Env)
    private String compressThreshold = "10M";

    @Overwrite(env = @Env)
    private int reconciliationIntervalHours = 24;

    public long defaultTotalBytes() {
        return SizeParser.parseBytes(defaultTotal);
    }

    public long defaultKbBytes() {
        return SizeParser.parseBytes(defaultKb);
    }

    public long defaultBoardBytes() {
        return SizeParser.parseBytes(defaultBoard);
    }

    public long defaultImagesBytes() {
        return SizeParser.parseBytes(defaultImages);
    }

    public long defaultPagesBytes() {
        return SizeParser.parseBytes(defaultPages);
    }

    public long defaultPerFileBytes() {
        return SizeParser.parseBytes(defaultPerFile);
    }

    public long defaultPerImageBytes() {
        return SizeParser.parseBytes(defaultPerImage);
    }

    public int warningThresholdPercent() {
        return warningThresholdPercent;
    }

    public boolean compressPresentations() {
        return compressPresentations;
    }

    public long compressThresholdBytes() {
        return SizeParser.parseBytes(compressThreshold);
    }

    public int reconciliationIntervalHours() {
        return reconciliationIntervalHours;
    }
}
