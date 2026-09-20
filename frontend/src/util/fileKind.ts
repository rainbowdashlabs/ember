/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** What a file can be shown as, which decides both its tile and how it opens. */
export type FileKind = 'image' | 'pdf' | 'video' | 'audio' | 'text' | 'other'

/** What a name's ending says the file is, for the types worth reading on screen. */
const BY_EXTENSION: Record<string, FileKind> = {
    pdf: 'pdf',
    png: 'image', jpg: 'image', jpeg: 'image', gif: 'image', webp: 'image', svg: 'image', avif: 'image',
    mp4: 'video', webm: 'video', mov: 'video',
    mp3: 'audio', wav: 'audio', ogg: 'audio', m4a: 'audio',
    txt: 'text', csv: 'text', log: 'text', md: 'text',
}

/**
 * What kind of thing a file is, read off the type it was stored under and, failing that, its name.
 *
 * <p>The one place that answers it, so a tile, a preview and the decision whether to ask for a
 * picture at all cannot drift into three different opinions about the same file.
 *
 * <p>The name is asked only where the type says nothing, which is the common case for anything a
 * member uploaded: a browser that offered no type, or a server falling back to the stream of bytes
 * it could not name, both store {@code application/octet-stream}. Read from the type alone, a
 * perfectly readable report is a thing nobody can draw, and it goes down the saving path on a phone
 * that has nowhere to save it.
 *
 * @param mimeType the stored type, empty or generic where none was known
 * @param fileName the stored name, consulted only where the type gives nothing away
 */
export function fileKindOf(mimeType?: string | null, fileName?: string | null): FileKind {
    const mime = (mimeType ?? '').toLowerCase()
    if (mime.startsWith('image/')) return 'image'
    if (mime === 'application/pdf') return 'pdf'
    if (mime.startsWith('video/')) return 'video'
    if (mime.startsWith('audio/')) return 'audio'
    if (mime.startsWith('text/')) return 'text'
    return kindOfName(fileName)
}

/** What a file is called, where what it was stored as gives nothing away. */
function kindOfName(fileName?: string | null): FileKind {
    const ending = (fileName ?? '').toLowerCase().split('.').pop() ?? ''
    return BY_EXTENSION[ending] ?? 'other'
}

/**
 * Whether a file is one the reader can actually put on screen.
 *
 * <p>A picture, a document with pages, a recording: things with a viewer behind them. Everything
 * else is handed over to be saved, because opening it in the page would only show its bytes as
 * words, and a reader who wanted an export as words did not want it as a document at all.
 */
export function canBeRead(mimeType?: string | null, fileName?: string | null): boolean {
    const kind = fileKindOf(mimeType, fileName)
    return kind === 'image' || kind === 'pdf' || kind === 'video' || kind === 'audio'
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
