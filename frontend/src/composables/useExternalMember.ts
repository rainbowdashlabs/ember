/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, type ComputedRef} from 'vue'
import {useSession} from '@/composables/useSession'
import type {PersonIdentity} from '@/util/personIdentity'

/**
 * Whether a person belongs to another station than the one the reader is at, which is what a
 * station badge beside their name says and what keeps their card from being asked of this station.
 *
 * @param identity the person, read on every evaluation
 */
export function useExternalMember(identity: () => PersonIdentity | null | undefined): ComputedRef<boolean> {
    const {sessionInfo} = useSession()
    return computed(() => {
        const stationUid = identity()?.stationUid
        return !!stationUid && stationUid !== sessionInfo.value?.stationId
    })
}
