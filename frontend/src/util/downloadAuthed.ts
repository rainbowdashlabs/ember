/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from '@/api/client'
import {presentDocument} from '@/util/documentView'

/**
 * Extracts the filename from a Content-Disposition header value, honouring the RFC 5987
 * {@code filename*=UTF-8''…} form before the plain {@code filename="…"} form. Returns
 * null when the header is absent or carries no filename.
 */
export function parseContentDispositionFilename(header?: string | null): string | null {
    if (!header) return null
    const extended = header.match(/filename\*=(?:UTF-8'')?([^;]+)/i)?.[1]
    if (extended) return decodeURIComponent(extended.trim().replace(/^"|"$/g, ''))
    const plain = header.match(/filename="?([^";]+)"?/i)?.[1]
    return plain ? plain.trim() : null
}

/**
 * Fetches a file from an authenticated endpoint and hands it to the reader.
 *
 * <p>The request goes through the shared axios client, so the {@code Authorization} and
 * {@code X-Station-Id} headers are applied for it. What happens to the bytes afterwards is
 * {@link presentDocument}'s to decide: saved at a desk, and in the hand opened where there is a
 * viewer for them.
 *
 * <p>Handing them over rather than saving them directly is what makes a slow download work on a
 * phone at all. Saving asks the system for its share sheet, and the system grants that only to a
 * press the reader has just made: by the time a large file has arrived, the press it began with has
 * expired, the sheet is refused, and the fallback is the download link that does nothing there. The
 * reader's own save button asks again from a fresh press, with the bytes already in hand.
 *
 * @param url      relative API path (e.g. {@code /kb/files/42/original}).
 * @param filename name suggested to the browser's save dialog; when omitted, the name
 *                 from the response's Content-Disposition header is used, falling back
 *                 to the last URL segment.
 */
export async function downloadAuthed(url: string, filename?: string): Promise<void> {
    const res = await client.get(url, {responseType: 'blob'})
    const resolved = filename
        ?? parseContentDispositionFilename(res.headers['content-disposition'] as string | undefined)
        ?? url.split('/').pop()
        ?? 'download'
    const type = (res.headers['content-type'] as string | undefined) ?? ''
    await presentDocument(res.data as Blob, resolved, type.split(';')[0]?.trim() || undefined)
}
