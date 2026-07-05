/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import i18n from '~/i18n'

let merged = false

/**
 * Lazily loads the (large) help-center translations and merges them into the active
 * locale. The help-center text lives in its own chunk so the station/admin SPA bundle
 * does not carry it; the chunk is fetched only when a help-center layout renders, on
 * both the server (ISR) and the client (navigation/hydration).
 */
export async function loadHelpcenterMessages(): Promise<void> {
    if (merged) return
    const messages = (await import('~/i18n/de-DE.helpcenter')).default
    i18n.global.mergeLocaleMessage('de-DE', {helpCenter: messages})
    merged = true
}
