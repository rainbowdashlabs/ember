/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {readonly} from 'vue'
import {session} from '@/api'
import {getItem} from '@/api/storage'
import {usePermissions} from '@/composables/usePermissions'
import {claimVisitedArea} from '@/util/landingMemoryState'
import {
    sessionClusterId,
    sessionInfo,
    sessionLoadFailed,
    sessionLoaded,
    sessionStationId,
} from '@/util/sessionState'

let loadSeq = 0

function isTransientError(error: unknown): boolean {
    const status = (error as {response?: {status?: number}})?.response?.status
    return status == null || status >= 500
}

/**
 * Lifecycle of the signed-in session plus the account and station state derived from it.
 * Permission checks live in {@link usePermissions} and are composed in here so call sites
 * keep a single entry point.
 */
export function useSession() {
    async function load() {
        const seq = ++loadSeq
        const requestedStation = getItem('station_id')
        const requestedCluster = getItem('cluster_id')
        for (let attempt = 1; ; attempt++) {
            try {
                const info = await session.getSessionInfo()
                if (seq !== loadSeq) return
                sessionInfo.value = info
                sessionLoadFailed.value = false
                break
            } catch (error) {
                if (seq !== loadSeq) return
                if (!isTransientError(error) || attempt >= 3) {
                    sessionInfo.value = null
                    sessionLoadFailed.value = true
                    break
                }
                await new Promise(resolve => setTimeout(resolve, 500 * attempt))
            }
        }
        sessionStationId.value = requestedStation
        sessionClusterId.value = requestedCluster
        sessionLoaded.value = true
        claimVisitedArea()
    }

    function clear() {
        loadSeq++
        sessionInfo.value = null
        sessionLoaded.value = false
        sessionLoadFailed.value = false
        sessionStationId.value = null
        sessionClusterId.value = null
    }

    function fullName(): string {
        const account = sessionInfo.value?.account
        if (!account) return ''
        return [account.firstName, account.lastName].filter(Boolean).join(' ')
    }

    function isKbPublic(): boolean {
        return sessionInfo.value?.publicKbMode != null && sessionInfo.value.publicKbMode !== 'OFF'
    }

    return {
        sessionInfo: readonly(sessionInfo),
        loaded: readonly(sessionLoaded),
        loadFailed: readonly(sessionLoadFailed),
        sessionStationId: readonly(sessionStationId),
        sessionClusterId: readonly(sessionClusterId),
        load,
        clear,
        fullName,
        isKbPublic,
        ...usePermissions(),
    }
}
