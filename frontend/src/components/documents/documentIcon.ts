/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/**
 * The icon a tile falls back to when no picture could be made of the file, which is everything
 * that is neither an image nor a document with pages.
 */
export function iconFor(mimeType: string | undefined): string {
    if (!mimeType) return 'file'
    if (mimeType.startsWith('image/')) return 'image'
    if (mimeType === 'application/pdf') return 'file-pdf'
    if (mimeType.startsWith('text/csv')) return 'file-csv'
    if (mimeType.startsWith('text/')) return 'file-lines'
    if (mimeType.includes('presentation')) return 'file-powerpoint'
    return 'file'
}

/** The size of a file in the units a reader thinks in. */
export function formatSize(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`
    if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`
    return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}
