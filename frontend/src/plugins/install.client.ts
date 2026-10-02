/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {keepInstallOffer, markInstalled, type InstallOffer} from '@/util/installPrompt'

/**
 * Registers the service worker that makes Ember installable, and catches the browser's offer to
 * install it. A worker that fails to register costs nothing but installability, so the failure is
 * ignored.
 *
 * The offer is made once, unasked, and is lost unless it is caught and held, so the browser's own
 * banner is prevented and the offer kept for the moment the reader is actually asked.
 */
export default defineNuxtPlugin(() => {
    if ('serviceWorker' in navigator) {
        window.addEventListener('load', () => {
            navigator.serviceWorker.register('/sw.js').catch(() => {})
        })
    }

    window.addEventListener('beforeinstallprompt', event => {
        event.preventDefault()
        keepInstallOffer(event as InstallOffer)
    })

    window.addEventListener('appinstalled', () => markInstalled())
})
