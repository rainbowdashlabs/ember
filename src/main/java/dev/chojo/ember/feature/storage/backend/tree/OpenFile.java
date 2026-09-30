/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend.tree;

import java.io.InputStream;

/**
 * A file of a {@link FileTree} opened for reading.
 *
 * @param body the file's bytes; closing it releases the file
 * @param size the file's size in bytes
 */
public record OpenFile(InputStream body, long size) {}
