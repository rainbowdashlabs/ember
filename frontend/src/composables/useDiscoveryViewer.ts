/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, type ComputedRef} from 'vue'
import {useSession} from '@/composables/useSession'

/**
 * Who is looking at the discovery tiles, as far as the buttons on them care.
 *
 * <p>`mayRequestFederation` holds for someone signed in on this instance with the federation permission at
 * their current station, `stationUid` names that station, and `offersInvite` says whether the page hands out
 * invite codes at all.
 */
export interface DiscoveryViewer {
    mayRequestFederation: boolean
    stationUid: string | null
    offersInvite: boolean
}

/**
 * The viewer of a discovery page, from the session.
 *
 * @param offersInvite whether the page hands out invite codes
 */
export function useDiscoveryViewer(offersInvite: boolean): ComputedRef<DiscoveryViewer> {
    const {sessionInfo, canManageFederation} = useSession()
    return computed(() => ({
        mayRequestFederation: !!sessionInfo.value && canManageFederation(),
        stationUid: sessionInfo.value?.stationId ?? null,
        offersInvite,
    }))
}
