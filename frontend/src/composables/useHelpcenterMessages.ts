/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
const merging = new WeakMap<object, Promise<void>>()

/**
 * Lazily loads the (large) help-center translations and merges them into the German messages of
 * the current app's i18n instance. The help-center text lives in its own chunk so no other page
 * carries it; the chunk is fetched only when a help-center layout renders, on both the server (ISR)
 * and the client (navigation/hydration).
 *
 * <p>What has been merged is remembered per instance, not per process: the server builds an
 * instance for every request, and each of them needs the text merged into it once. A failed load is
 * forgotten, so the next help page tries again.
 *
 * <p>Call it during setup, before any `await`, since it reads the instance from the Nuxt app.
 */
export function loadHelpcenterMessages(): Promise<void> {
    const i18n = useNuxtApp().$i18n
    const pending = merging.get(i18n)
    if (pending) return pending

    const loading = import('~/i18n/de-DE.helpcenter')
        .then(module => i18n.mergeLocaleMessage('de-DE', {helpCenter: module.default}))
        .catch((error: unknown) => {
            merging.delete(i18n)
            throw error
        })
    merging.set(i18n, loading)
    return loading
}
