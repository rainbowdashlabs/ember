/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/**
 * A place text in the form the server keys its pins by: trimmed, in lower case, every run of
 * whitespace made one space. Two spellings of one place share one pin.
 */
export function normalisePlace(text: string): string {
    return text.trim().replace(/\s+/g, ' ').toLowerCase()
}
