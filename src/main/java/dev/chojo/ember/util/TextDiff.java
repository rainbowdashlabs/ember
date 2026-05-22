/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.PatchFailedException;

import java.util.List;

/**
 * Unified diff patch creation and application using java-diff-utils.
 * Used for knowledgebase markdown versioning and legal document diffs.
 */
public final class TextDiff {
    private TextDiff() {}

    /**
     * Creates a unified diff patch between two text contents.
     *
     * @return unified diff string, or empty string if contents are identical
     */
    public static String createPatch(String oldContent, String newContent) {
        var oldLines = splitLines(oldContent);
        var newLines = splitLines(newContent);

        var patch = DiffUtils.diff(oldLines, newLines);
        if (patch.getDeltas().isEmpty()) return "";

        var unifiedDiff = UnifiedDiffUtils.generateUnifiedDiff("a", "b", oldLines, patch, 3);
        return String.join("\n", unifiedDiff);
    }

    /**
     * Applies a unified diff patch to base content.
     *
     * @return the patched content
     * @throws IllegalArgumentException if the patch cannot be applied
     */
    public static String applyPatch(String baseContent, String patchText) {
        if (patchText == null || patchText.isBlank()) return baseContent;

        var baseLines = splitLines(baseContent);
        var diffLines = List.of(patchText.split("\n", -1));

        var patch = UnifiedDiffUtils.parseUnifiedDiff(diffLines);
        try {
            var result = DiffUtils.patch(baseLines, patch);
            return String.join("\n", result);
        } catch (PatchFailedException e) {
            throw new IllegalArgumentException("Failed to apply patch: " + e.getMessage(), e);
        }
    }

    /**
     * Generates a human-readable diff summary between two texts.
     * Each changed section shows removed lines prefixed with "- " and added lines prefixed with "+ ".
     */
    public static String generateDiffSummary(String oldContent, String newContent) {
        var oldLines = splitLines(oldContent);
        var newLines = splitLines(newContent);

        var patch = DiffUtils.diff(oldLines, newLines);
        if (patch.getDeltas().isEmpty()) return "No changes.";

        var sb = new StringBuilder();
        for (var delta : patch.getDeltas()) {
            sb.append("@@ Line ").append(delta.getSource().getPosition() + 1).append(" @@\n");
            for (var line : delta.getSource().getLines()) {
                sb.append("- ").append(line).append("\n");
            }
            for (var line : delta.getTarget().getLines()) {
                sb.append("+ ").append(line).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private static List<String> splitLines(String text) {
        if (text == null || text.isEmpty()) return List.of();
        return List.of(text.split("\n", -1));
    }
}
