/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */

/**
 * Returns {@code true} when the given src points at an inline KB image
 * served by the authenticated API. Such images must be fetched through the
 * axios client rather than rendered directly by the browser.
 */
export function isKbImageSrc(src: string): boolean {
    return !!src && src.startsWith('/kb/images/')
}
