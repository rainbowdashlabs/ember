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

import java.util.List;
import java.util.TreeSet;

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

    /**
     * Extends presentation recompression to the full ZIP-based Office family:
     * {@code .docx} / {@code .xlsx} / {@code .odt} / {@code .ods}. Same maximum-
     * deflate rewrite — typically 10–25% smaller, fully lossless, completely transparent to
     * downstream readers.
     */
    @Overwrite(env = @Env)
    private boolean compressOfficeDocs = true;

    /**
     * Whether to losslessly recompress PDF uploads via {@code qpdf --linearize
     * --object-streams=generate}. Skips when {@code qpdf} is not available on PATH; see
     * {@code QPDF_BIN}.
     */
    @Overwrite(env = @Env)
    private boolean compressPdfs = true;

    /**
     * Whether to gzip plain-text / Markdown / JSON / XML / YAML uploads on disk. The HTTP
     * layer keeps decompressed responses correct; this is purely a disk-footprint win.
     */
    @Overwrite(env = @Env)
    private boolean compressTextFiles = true;

    @Overwrite(env = @Env)
    private String compressThreshold = "10M";

    @Overwrite(env = @Env)
    private int reconciliationIntervalHours = 24;

    /**
     * Whether to pre-generate width-keyed image variants on upload. Variants
     * cut public-page egress by an order of magnitude on image-heavy pages because the client
     * downloads a 1024 px WebP instead of a 4 MB original. Disable only on very constrained
     * deployments where the ~1 s upload-time CPU cost is unwelcome — variants remain absent
     * and the original is served for every request.
     */
    @Overwrite(env = @Env)
    private boolean imageVariantsEnabled = true;

    /**
     * Comma-separated list of widths (in pixels) to pre-generate at upload time. Each width
     * produces a resized copy in the original format plus a WebP copy when
     * {@link #imageVariantsWebp()} is on.
     */
    @Overwrite(env = @Env)
    private String imageVariantsWidths = "128,256,512,1024,2048";

    /**
     * Whether to emit a WebP copy alongside each width. WebP is supported by every modern
     * browser and shaves another ~30% over the resized original-format variant. Disable only
     * when there is a specific reason to suppress WebP delivery.
     */
    @Overwrite(env = @Env)
    private boolean imageVariantsWebp = true;

    /**
     * Selects which backend the resolver picks for movable categories and carries the per-
     * backend connection settings. Local-pinned categories ignore this and stay on the local
     * disk regardless.
     */
    private StorageBackendSettings backend = new StorageBackendSettings();

    /**
     * AES-256 key used to encrypt station-supplied remote-backend credentials at rest. Read
     * from {@code STORAGE_CREDENTIAL_ENCRYPTION_KEY}; required once any station opts into
     * self-service remote storage. Auto-generated and persisted on first production boot
     * when left blank.
     */
    @Overwrite(env = @Env)
    private String credentialEncryptionKey = "";

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

    public boolean compressOfficeDocs() {
        return compressOfficeDocs;
    }

    public boolean compressPdfs() {
        return compressPdfs;
    }

    public boolean compressTextFiles() {
        return compressTextFiles;
    }

    public long compressThresholdBytes() {
        return SizeParser.parseBytes(compressThreshold);
    }

    public int reconciliationIntervalHours() {
        return reconciliationIntervalHours;
    }

    public boolean imageVariantsEnabled() {
        return imageVariantsEnabled;
    }

    public String imageVariantsWidths() {
        return imageVariantsWidths;
    }

    public boolean imageVariantsWebp() {
        return imageVariantsWebp;
    }

    public StorageBackendSettings backend() {
        return backend;
    }

    public String credentialEncryptionKey() {
        return credentialEncryptionKey;
    }

    /**
     * Parses {@link #imageVariantsWidths()} into a sorted, deduplicated list of positive
     * integers. Invalid tokens are silently skipped — the config is operator-supplied and the
     * application should never crash because of an extra comma.
     */
    public List<Integer> imageVariantsWidthList() {
        var out = new TreeSet<Integer>();
        if (imageVariantsWidths != null) {
            for (String token : imageVariantsWidths.split(",")) {
                String trimmed = token.trim();
                if (trimmed.isEmpty()) continue;
                try {
                    int width = Integer.parseInt(trimmed);
                    if (width > 0) out.add(width);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return List.copyOf(out);
    }
}
