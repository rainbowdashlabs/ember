/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** What a file can be shown as, which decides both its tile and how it opens. */
export type FileKind = 'image' | 'pdf' | 'video' | 'audio' | 'text' | 'other'

/**
 * What kind of thing a file is, read off the type it was stored under.
 *
 * <p>The one place that answers it, so a tile, a preview and the decision whether to ask for a
 * picture at all cannot drift into three different opinions about the same file.
 */
export function fileKindOf(mimeType?: string | null): FileKind {
    const mime = (mimeType ?? '').toLowerCase()
    if (mime.startsWith('image/')) return 'image'
    if (mime === 'application/pdf') return 'pdf'
    if (mime.startsWith('video/')) return 'video'
    if (mime.startsWith('audio/')) return 'audio'
    if (mime.startsWith('text/')) return 'text'
    return 'other'
}

/**
 * Whether asking the server for a picture of this file is worth a request.
 *
 * <p>Matched to what the server will answer: an image is its own picture and a document with pages
 * is its first one. Anything else is drawn as its kind instead, and asking would only earn a refusal.
 */
export function canHavePicture(mimeType?: string | null): boolean {
    const kind = fileKindOf(mimeType)
    return kind === 'image' || kind === 'pdf'
}

/**
 * The icon standing in for a file that has no picture of its own.
 *
 * <p>A few types are worth telling apart more finely than they are worth drawing differently: a
 * spreadsheet and a log file are both text on screen, and a reader scanning a list of names still
 * wants to see which is which.
 */
export function fileKindIcon(mimeType?: string | null): string[] {
    const mime = (mimeType ?? '').toLowerCase()
    if (mime.startsWith('text/csv')) return ['fas', 'file-csv']
    if (mime.includes('presentation')) return ['fas', 'file-powerpoint']
    switch (fileKindOf(mimeType)) {
        case 'image':
            return ['fas', 'image']
        case 'pdf':
            return ['fas', 'file-pdf']
        case 'video':
            return ['fas', 'film']
        case 'audio':
            return ['fas', 'music']
        case 'text':
            return ['fas', 'file-lines']
        default:
            return ['fas', 'file']
    }
}
