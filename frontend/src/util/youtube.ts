/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/**
 * Returns the YouTube video id embedded in the given URL, or {@code null}
 * if the URL does not match any recognised YouTube share or embed pattern.
 * Supports {@code youtube.com/watch?v=...}, {@code youtu.be/...} and
 * {@code youtube.com/embed/...} shapes.
 */
export function extractYoutubeId(url: string): string | null {
    const patterns = [
        /(?:youtube\.com\/watch\?v=)([a-zA-Z0-9_-]{11})/,
        /(?:youtu\.be\/)([a-zA-Z0-9_-]{11})/,
        /(?:youtube\.com\/embed\/)([a-zA-Z0-9_-]{11})/,
    ]
    for (const pattern of patterns) {
        const match = url.match(pattern)?.[1]
        if (match) return match
    }
    return null
}

/**
 * Returns {@code true} when the URL looks like a YouTube link based on its host.
 */
export function isYoutubeUrl(url: string): boolean {
    return /youtube\.com|youtu\.be/.test(url)
}

/**
 * Returns the privacy-friendly nocookie embed URL for the given YouTube link,
 * or {@code null} when no video id can be extracted.
 */
export function youtubeEmbedUrl(url: string): string | null {
    const id = extractYoutubeId(url)
    return id ? `https://www.youtube-nocookie.com/embed/${id}` : null
}

/**
 * Returns the embeddable player URL for a video share link from any provider the editor accepts,
 * falling back to the URL itself when none matches - a self-hosted player usually already is its
 * own embed URL, and refusing it outright would be worse than trying.
 */
export function videoEmbedUrl(url: string): string {
    const youtube = youtubeEmbedUrl(url)
    if (youtube) return youtube

    const vimeo = url.match(/vimeo\.com\/(\d+)/)
    if (vimeo) return `https://player.vimeo.com/video/${vimeo[1]}`

    const peertube = url.match(/(https?:\/\/[^/]+)\/videos\/watch\/(.+)/)
    if (peertube) return `${peertube[1]}/videos/embed/${peertube[2]}`

    const dailymotion = url.match(/(?:dailymotion\.com\/video\/|dai\.ly\/)([a-zA-Z0-9]+)/)
    if (dailymotion) return `https://www.dailymotion.com/embed/video/${dailymotion[1]}`

    return url
}
