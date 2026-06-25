/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/**
 * Formats a byte count as a human-readable string using binary (1024) units.
 * Uses {@code 0 B} for zero and rounds to one fractional digit for values
 * larger than a byte (e.g. {@code 1.5 MiB}).
 */
export function formatBytes(bytes: number): string {
    if (bytes === 0) return '0 B'
    const units = ['B', 'KiB', 'MiB', 'GiB', 'TiB']
    const i = Math.min(units.length - 1, Math.floor(Math.log(bytes) / Math.log(1024)))
    const value = bytes / Math.pow(1024, i)
    return `${value.toFixed(i === 0 ? 0 : 1)} ${units[i]}`
}

/**
 * Translation function shape accepted by {@link buildStorageCategoryLabeler}.
 * Matches the {@code t} returned by {@code useI18n()} closely enough to be
 * passed in directly without a cast.
 */
export type StorageLabelTranslator = (key: string) => string

/**
 * Builds a category-to-display-name function bound to a translator. The
 * returned function maps a backend {@link StorageCategoryName} (or any other
 * string for forward compatibility) to its localized display name, falling
 * back to the raw category code when no translation is registered.
 */
export function buildStorageCategoryLabeler(t: StorageLabelTranslator): (cat: string) => string {
    const labels: Record<string, string> = {
        KB_FILES: t('storageMonitoring.categories.kbFiles'),
        BOARD_ATTACHMENTS: t('storageMonitoring.categories.boardAttachments'),
        PAGE_FILES: t('storageMonitoring.categories.pageFiles'),
        PAGE_IMAGES: t('storageMonitoring.categories.pageFiles'),
        IMAGE_AVATAR: t('storageMonitoring.categories.avatars'),
        IMAGE_LOST_AND_FOUND: t('storageMonitoring.categories.lostAndFound'),
        IMAGE_LOGO_FRAGMENT: t('storageMonitoring.categories.logoFragment'),
        IMAGE_QUIZ_QUESTION: t('storageMonitoring.categories.quizQuestion'),
        IMAGE_KB_ICON: t('storageMonitoring.categories.kbIcon'),
        IMAGE_KB_IMAGE: t('storageMonitoring.categories.kbImage'),
        DOCUMENT: t('storageMonitoring.categories.document'),
        DISCOVERY_KEY: t('storageMonitoring.categories.discoveryKey'),
        MAP_TILE_CACHE: t('storageMonitoring.categories.mapTileCache'),
        DEMO_AVATAR: t('storageMonitoring.categories.demoAvatar'),
    }
    return (cat: string) => labels[cat] ?? cat
}

/**
 * Shared color palette used by storage usage charts and stacked bars so the
 * same category renders in the same color everywhere. Categories without an
 * entry fall back to the neutral gray {@code #9ca3af} at the call site.
 */
export const STORAGE_CATEGORY_COLORS: Record<string, string> = {
    KB_FILES: '#3694FF',
    BOARD_ATTACHMENTS: '#FF6421',
    PAGE_FILES: '#00C507',
    IMAGE_LOST_AND_FOUND: '#73CEFF',
    IMAGE_QUIZ_QUESTION: '#FFDD1B',
    IMAGE_KB_ICON: '#C71100',
    IMAGE_KB_IMAGE: '#3694FF',
    IMAGE_LOGO_FRAGMENT: '#9333EA',
}
