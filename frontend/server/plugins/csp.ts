/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {randomBytes} from 'node:crypto'
import {defineNitroPlugin, useRuntimeConfig} from '#imports'

/**
 * The content security policy for the pages this server renders.
 *
 * <p>Scripts are the point of it. A page carries three inline scripts of its own (the theme
 * switch, the configuration Nuxt hands to the browser, and the import map that names the entry
 * chunk), so the policy cannot simply refuse inline script: instead every one of them is stamped
 * with a nonce minted for that response, and an injected script, which has no way to know the
 * nonce, is refused.
 *
 * Styles cannot work that way. A rendered page carries over a hundred inline `style` attributes
 * from ordinary Vue bindings, and a style attribute has nowhere to put a nonce, so `unsafe-inline`
 * stays for styles until the bindings themselves are rewritten. That is a real limit and worth
 * knowing: the policy defends against injected script, not against injected style.
 *
 * Images, frames and media accept any HTTPS source, because an operator points the map at their own
 * tile server and an author embeds a video from wherever their group hosts it.
 */

/** How the policy is sent. Report-only observes and reports; enforce refuses. */
const MODES = ['report', 'enforce', 'off'] as const
type CspMode = (typeof MODES)[number]

const DIRECTIVES = (nonce: string): string[] => [
    "default-src 'self'",
    `script-src 'self' 'nonce-${nonce}'`,
    "style-src 'self' 'unsafe-inline'",
    "img-src 'self' data: blob: https:",
    "font-src 'self' data:",
    "connect-src 'self' https://api.github.com",
    "frame-src 'self' blob: https:",
    "media-src 'self' blob: https:",
    "worker-src 'self' blob:",
    "object-src 'none'",
    "base-uri 'self'",
    "form-action 'self'",
    "frame-ancestors 'self'",
]

function resolveMode(configured: unknown): CspMode {
    return MODES.includes(configured as CspMode) ? (configured as CspMode) : 'report'
}

/**
 * Stamps the nonce on every inline script of a rendered fragment. Tags that already carry one are
 * left alone, and a tag that only loads a file needs none, but stamping it costs nothing and keeps
 * the rule to one expression.
 */
function withNonce(fragment: string, nonce: string): string {
    return fragment.replace(/<script(?![^>]*\bnonce=)/g, `<script nonce="${nonce}"`)
}

export default defineNitroPlugin((nitroApp) => {
    nitroApp.hooks.hook('render:html', (html, {event}) => {
        const mode = resolveMode(useRuntimeConfig(event).cspMode)
        if (mode === 'off') return

        const nonce = randomBytes(16).toString('base64')
        for (const part of [html.head, html.bodyPrepend, html.bodyAppend] as string[][]) {
            for (let i = 0; i < part.length; i++) part[i] = withNonce(part[i], nonce)
        }

        const header = mode === 'enforce' ? 'Content-Security-Policy' : 'Content-Security-Policy-Report-Only'
        event.node.res.setHeader(header, DIRECTIVES(nonce).join('; '))
        event.node.res.setHeader('X-Frame-Options', 'SAMEORIGIN')
        event.node.res.setHeader('X-Content-Type-Options', 'nosniff')
        event.node.res.setHeader('Referrer-Policy', 'strict-origin-when-cross-origin')
    })
})
