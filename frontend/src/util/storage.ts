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

/** The units room is written in on a screen, because nobody types a quota in bytes. */
export const SIZE_UNITS = ['MiB', 'GiB', 'TiB'] as const

export type SizeUnit = (typeof SIZE_UNITS)[number]

/** A size as somebody edits it: a number and the unit it is counted in. */
export interface SizeField {
    value: number
    unit: SizeUnit
}

const UNIT_MULTIPLIER: Record<SizeUnit, number> = {
    MiB: 1024 * 1024,
    GiB: 1024 * 1024 * 1024,
    TiB: 1024 * 1024 * 1024 * 1024,
}

/** Reads a byte count as the largest unit it is a sensible number in. */
export function bytesToUnit(bytes: number): SizeField {
    if (bytes >= UNIT_MULTIPLIER.TiB) {
        return {value: Math.round((bytes / UNIT_MULTIPLIER.TiB) * 100) / 100, unit: 'TiB'}
    }
    if (bytes >= UNIT_MULTIPLIER.GiB) {
        return {value: Math.round((bytes / UNIT_MULTIPLIER.GiB) * 100) / 100, unit: 'GiB'}
    }
    return {value: Math.round((bytes / UNIT_MULTIPLIER.MiB) * 100) / 100, unit: 'MiB'}
}

export function toBytes(field: SizeField): number {
    return Math.round(field.value * UNIT_MULTIPLIER[field.unit])
}

/** The seven dimensions room is measured in, in the order a screen asks for them. */
export const QUOTA_FIELD_KEYS = ['total', 'kb', 'board', 'images', 'pages', 'perFile', 'perImage'] as const

export type QuotaFieldKey = (typeof QUOTA_FIELD_KEYS)[number]

export const QUOTA_FIELD_LABELS: Record<QuotaFieldKey, string> = {
    total: 'storageMonitoring.total',
    kb: 'storageMonitoring.categories.kbFiles',
    board: 'storageMonitoring.categories.boardAttachments',
    images: 'storageMonitoring.categories.images',
    pages: 'storageMonitoring.categories.pageImages',
    perFile: 'storageMonitoring.perFile',
    perImage: 'storageMonitoring.perImage',
}

/**
 * One dimension as somebody edits it.
 *
 * <p>An empty number means the sender is not deciding that dimension, which only an association can say. A
 * tier has to name every one of them, so the screen that keeps tiers fills them all in before it opens.
 */
export interface QuotaField {
    value: number | undefined
    unit: SizeUnit
}

export type QuotaFields = Record<QuotaFieldKey, QuotaField>

/** Every dimension left open, which is what an association starts a grant from. */
export function emptyQuotaFields(): QuotaFields {
    return {
        total: {value: undefined, unit: 'GiB'},
        kb: {value: undefined, unit: 'GiB'},
        board: {value: undefined, unit: 'GiB'},
        images: {value: undefined, unit: 'GiB'},
        pages: {value: undefined, unit: 'MiB'},
        perFile: {value: undefined, unit: 'MiB'},
        perImage: {value: undefined, unit: 'MiB'},
    }
}

/** A sensible set to start a new tier from, since a tier has to name every dimension. */
export function defaultQuotaFields(): QuotaFields {
    return {
        total: {value: 5, unit: 'GiB'},
        kb: {value: 4, unit: 'GiB'},
        board: {value: 3, unit: 'GiB'},
        images: {value: 1, unit: 'GiB'},
        pages: {value: 500, unit: 'MiB'},
        perFile: {value: 50, unit: 'MiB'},
        perImage: {value: 5, unit: 'MiB'},
    }
}

/** Reads a byte count back into a field, leaving it open when there is none. */
export function fieldFromBytes(bytes: number | null | undefined): QuotaField {
    if (bytes === null || bytes === undefined) return {value: undefined, unit: 'GiB'}
    return bytesToUnit(bytes)
}

/** Reads a field back as bytes, or as nothing at all when it was left empty or cleared. */
export function fieldToBytes(field: QuotaField): number | null {
    const value = Number(field.value)
    if (field.value === undefined || field.value === null || Number.isNaN(value)) return null
    return toBytes({value, unit: field.unit})
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
