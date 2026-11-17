/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {keepInstallOffer, markInstalled, type InstallOffer} from '@/util/installPrompt'

/**
 * Registers the service worker that makes Ember installable and receives its push messages, and
 * catches the browser's offer to install it. The page has often finished loading by the time this
 * runs, so it registers at once then rather than waiting for a load that already happened. A worker
 * that fails to register costs installability and pushes, not the page, so the failure is ignored.
 *
 * The offer is made once, unasked, and is lost unless it is caught and held, so the browser's own
 * banner is prevented and the offer kept for the moment the reader is actually asked.
 */
export default defineNuxtPlugin(() => {
    if ('serviceWorker' in navigator) {
        const register = () => navigator.serviceWorker.register('/sw.js').catch(() => {})
        if (document.readyState === 'complete') void register()
        else window.addEventListener('load', register, {once: true})
    }

    window.addEventListener('beforeinstallprompt', event => {
        event.preventDefault()
        keepInstallOffer(event as InstallOffer)
    })

    window.addEventListener('appinstalled', () => markInstalled())
})
