/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, onMounted} from 'vue'
import {useCluster} from '@/composables/useCluster'
import {useActingStation} from '@/composables/useActingStation'

/**
 * Acts at the station the current association owns, for as long as the screen is open.
 *
 * <p>The association's knowledge base, news and calendar are kept there, and are shown through the station's
 * own screens. Those screens ask for "the station", so while one of them is open on the association's side,
 * the station is that one.
 *
 * <p>The returned id is null until the association is known. A page renders the screen only once it is not,
 * so that no request leaves for the wrong station first.
 *
 * @return the station the association owns
 */
export function useClusterHomeStation() {
    const {activeCluster, loaded, load} = useCluster()

    const homeStationId = computed(() => activeCluster.value?.homeStationId ?? null)

    useActingStation(homeStationId)

    onMounted(() => {
        if (!loaded.value) load()
    })

    return {homeStationId}
}
