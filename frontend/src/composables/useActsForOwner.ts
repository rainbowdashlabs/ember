/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, type ComputedRef} from 'vue'
import {useCluster} from '@/composables/useCluster'
import {getActingStation} from '@/util/actingStationState'

/**
 * Whether this screen is acting for the association that owns what it shows.
 *
 * <p>An association keeps its gear on the station it owns and edits it through that station's own screens,
 * so its requests are station requests and look like anybody else's. The one thing that tells them apart is
 * that the association being acted for is the one whose station is being acted at.
 *
 * <p>The screens use it to decide what to offer: a station holding an association's jacket may not rename or
 * delete it, while the association itself may, and the same screen draws both.
 *
 * @return true while the reader acts for an association at that association's own station
 */
export function useActsForOwner(): ComputedRef<boolean> {
    const {activeCluster} = useCluster()

    return computed(() => {
        const cluster = activeCluster.value
        return cluster != null && cluster.homeStationId === getActingStation()
    })
}
