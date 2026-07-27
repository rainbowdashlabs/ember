/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/**
 * Derives a machine-friendly identifier from a human label: lower-cased, German umlauts
 * transliterated, remaining diacritics stripped, and everything else collapsed to
 * underscores so the result matches the backend's key pattern (^[a-z][a-z0-9_]*$).
 */
export function harmonizeKey(label: string): string {
    return label
        .toLowerCase()
        .replace(/ä/g, 'ae')
        .replace(/ö/g, 'oe')
        .replace(/ü/g, 'ue')
        .replace(/ß/g, 'ss')
        .normalize('NFD')
        .replace(/[\u0300-\u036f]/g, '')
        .replace(/[^a-z0-9]+/g, '_')
        .replace(/^[0-9_]+/, '')
        .replace(/_+$/, '')
}
