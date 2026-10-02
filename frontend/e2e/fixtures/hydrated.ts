/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Page} from '@playwright/test'

/**
 * Waits until Nuxt has finished taking up the page the server sent.
 *
 * <p>A server-rendered page shows its content before the browser has taken it up, and a click or a
 * keystroke in that window lands on markup nothing listens to yet. A story that answers a form on
 * such a page waits for this first, the way a reader only gets to it once the page is alive.
 */
export async function hydrated(page: Page): Promise<void> {
    await page.waitForFunction(() => {
        const nuxt = (window as unknown as {useNuxtApp?: () => {isHydrating: boolean}}).useNuxtApp
        return nuxt !== undefined && !nuxt().isHydrating
    })
}
