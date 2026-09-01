/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {onMounted} from 'vue'
import {useRoute} from 'vue-router'
import {useCluster} from '@/composables/useCluster'
import {useStations} from '@/composables/useStations'
import {decideReturnLanding} from '@/util/returnLanding'

/**
 * Takes a reader who is already signed in from the landing page to the area they were last in.
 *
 * <p>It waits for the page to be mounted, which is not a detail: the landing page is rendered on the
 * server, where there is no browser to ask what anybody did last, and a route guard deciding this
 * would decide it in the middle of hydration, against markup that says something else. The page is
 * therefore rendered and sent exactly as it always was, and the reader is moved on once it stands.
 *
 * <p>The page stays reachable on purpose through {@code /?home}, and every link to it inside the
 * application carries that parameter. The move replaces the history entry rather than adding to it:
 * a page that redirects and can also be gone back to is a trap. Where the decision cannot be reached
 * at all, the landing page is where the reader stays.
 */
export function useReturnToLastArea(): void {
    const route = useRoute()
    const {setActiveStation} = useStations()
    const {setActiveCluster} = useCluster()

    onMounted(async () => {
        if (Object.hasOwn(route.query, 'home')) return
        const landing = await decideReturnLanding().catch(() => null)
        if (!landing) return
        if (landing.stationId) setActiveStation(landing.stationId)
        if (landing.clusterUid) setActiveCluster(landing.clusterUid)
        await navigateTo(landing.path, {replace: true})
    })
}
