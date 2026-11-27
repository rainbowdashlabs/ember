/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {useCluster} from '@/composables/useCluster'
import {useStations} from '@/composables/useStations'
import {namePlaceInLinks} from '@/util/placeInAddress'

/**
 * Lets every link name the station and cluster of this tab, so a link opened in a new tab opens the
 * same place.
 *
 * <p>Read from the app's state rather than from storage: on a page the server rendered, the state
 * learns the tab's place only once the page is taken up, so the links are drawn as the server drew
 * them and gain the place right after.
 */
export default defineNuxtPlugin(() => {
    const {currentStationId} = useStations()
    const {currentClusterId} = useCluster()
    namePlaceInLinks(useRouter(), () => currentStationId.value, () => currentClusterId.value)
})
