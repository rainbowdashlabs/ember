/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, readonly, ref} from 'vue'

/**
 * The browser's own offer to install Ember, held from the moment it is made until somebody asks
 * for it.
 *
 * No page can put a bookmark anywhere: there is no such API and there will not be one. Offering to
 * install is the one thing a page may ask for, and only browsers built on Chromium make the offer
 * at all. Everywhere else this stays empty and the written instructions are what the reader gets,
 * which is why nothing here ever reports a failure: there is nothing to have failed.
 */
export interface InstallOffer extends Event {
    prompt: () => Promise<void>
    userChoice: Promise<{outcome: 'accepted' | 'dismissed'}>
}

const offer = ref<InstallOffer | null>(null)
const installed = ref(false)

/** Whether the browser has an installation to offer right now. */
export const canInstall = computed(() => offer.value !== null)

/** Whether Ember is already installed, as far as this browser has said so. */
export const isInstalled = readonly(installed)

/** Keeps the offer the browser made, so it can be taken up later on the reader's own click. */
export function keepInstallOffer(event: InstallOffer) {
    offer.value = event
}

export function markInstalled() {
    installed.value = true
    offer.value = null
}

/**
 * Opens the browser's installation dialog and answers whether the reader accepted.
 *
 * The offer is good for one use, so it is let go of before the dialog opens rather than after: a
 * second click while the first dialog stands would otherwise be refused by the browser.
 */
export async function runInstall(): Promise<boolean> {
    const event = offer.value
    if (!event) return false
    offer.value = null
    await event.prompt()
    const choice = await event.userChoice
    if (choice.outcome === 'accepted') markInstalled()
    return choice.outcome === 'accepted'
}
