/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {restoreActiveCluster} from '@/composables/useCluster'
import {restoreActiveStation} from '@/composables/useStations'

/**
 * Brings back the station and the cluster the reader last worked for.
 *
 * <p>A page the server rendered knew neither, and the browser has to take it up the same way, so the
 * stored choice waits until that is done. A page rendered in the browser alone has nothing to agree
 * with, and gets the choice before it is drawn, so its first requests and its first render already know
 * where the reader works.
 */
export default defineNuxtPlugin((nuxtApp) => {
    function restore() {
        restoreActiveStation()
        restoreActiveCluster()
    }

    if (nuxtApp.payload.serverRendered) nuxtApp.hook('app:mounted', restore)
    else restore()
})
