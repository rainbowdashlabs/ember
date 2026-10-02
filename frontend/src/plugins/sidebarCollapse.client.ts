/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {restoreSidebarCollapse} from '@/composables/useSidebarCollapse'

/**
 * Brings back the sidebar the reader left collapsed.
 *
 * <p>A page the server rendered was drawn with the sidebar open, and the browser has to take it up
 * the same way, so the stored choice waits until that is done. A page rendered in the browser alone
 * has nothing to agree with, and gets the choice before it is drawn, so it never shows the sidebar
 * open for a moment first.
 */
export default defineNuxtPlugin((nuxtApp) => {
    if (nuxtApp.payload.serverRendered) nuxtApp.hook('app:mounted', restoreSidebarCollapse)
    else restoreSidebarCollapse()
})
