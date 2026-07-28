/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from '@/api/client'

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
 * Downloads a resource from an authenticated API endpoint and saves it to
 * disk under the given filename.
 *
 * The request is sent through the shared axios client so the
 * {@code Authorization} header and {@code X-Station-Id} header are applied
 * automatically. The response body is materialised as a {@link Blob} and
 * exposed to the browser via a temporary object URL that is revoked
 * immediately after the click is dispatched.
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
    saveBlob(res.data as Blob, resolved)
}

/**
 * Saves an already-materialised blob to disk via a temporary object URL that is revoked
 * immediately after the click is dispatched.
 */
export function saveBlob(blob: Blob, filename: string): void {
    const blobUrl = URL.createObjectURL(blob)
    try {
        const anchor = document.createElement('a')
        anchor.href = blobUrl
        anchor.download = filename
        anchor.rel = 'noopener'
        document.body.appendChild(anchor)
        anchor.click()
        anchor.remove()
    } finally {
        URL.revokeObjectURL(blobUrl)
    }
}
