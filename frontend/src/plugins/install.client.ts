/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {keepInstallOffer, markInstalled, type InstallOffer} from '@/util/installPrompt'

/**
 * Registers the service worker that makes Ember installable, and catches the browser's offer to
 * install it.
 *
 * The offer is made once, unasked, and is lost unless it is caught and held: preventing the
 * browser's own banner is what keeps it available for the moment the reader is actually asked,
 * which is where the first steps talk about keeping Ember to hand. A browser that never makes the
 * offer needs nothing here, and gets nothing.
 */
export default defineNuxtPlugin(() => {
    if ('serviceWorker' in navigator) {
        window.addEventListener('load', () => {
            navigator.serviceWorker.register('/sw.js').catch(() => {
                // Nothing is lost when this fails: without a worker Ember simply is not installable.
            })
        })
    }

    window.addEventListener('beforeinstallprompt', event => {
        event.preventDefault()
        keepInstallOffer(event as InstallOffer)
    })

    window.addEventListener('appinstalled', () => markInstalled())
})
