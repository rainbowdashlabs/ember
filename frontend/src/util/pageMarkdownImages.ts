/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
const PAGE_FILE_URL = /^\/api\/v1\/public\/pages\/[^/]+\/files\/[a-f0-9]+(?:$|\?)/i

/**
 * Post-processes marked-rendered HTML to upgrade {@code <img>} tags whose {@code src}
 * points at our public page-files endpoint ({@code /api/v1/public/pages/<uid>/files/<hash>}).
 * For every match, adds {@code loading="lazy"} + {@code decoding="async"} and rewrites the
 * URL to request the 1024 px variant — mirroring the "markdown-embedded inline
 * images → w=1024" default.
 *
 * <p>Non-page-file images (external CDNs, data URIs, third-party hosts) are left untouched
 * apart from the lazy / async attribute pair, which is universally safe.
 */
export function enhancePageMarkdownImages(html: string): string {
    return html.replace(/<img\b([^>]*)>/gi, (match, attrs: string) => {
        const enhanced = ensureAttribute(ensureAttribute(rewriteSrc(attrs), 'loading', 'lazy'), 'decoding', 'async')
        return `<img${enhanced}>`
    })
}

function rewriteSrc(attrs: string): string {
    return attrs.replace(/\s(src)=("([^"]*)"|'([^']*)')/i, (_full, _name, _quoted, dq, sq) => {
        const src = dq ?? sq ?? ''
        if (!PAGE_FILE_URL.test(src)) return ` src="${src}"`
        const sep = src.includes('?') ? '&' : '?'
        const withWidth = /[?&]w=\d+/.test(src) ? src : `${src}${sep}w=1024`
        return ` src="${withWidth}"`
    })
}

function ensureAttribute(attrs: string, name: string, value: string): string {
    if (new RegExp(`\\s${name}\\s*=`, 'i').test(attrs)) return attrs
    return `${attrs} ${name}="${value}"`
}
