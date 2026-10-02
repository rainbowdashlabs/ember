/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed} from 'vue'
import {session} from '@/api'
import {getItem} from '@/api/storage'
import {usePermissions} from '@/composables/usePermissions'
import {actingStationState} from '@/util/actingStationState'
import {claimVisitedArea} from '@/util/landingMemoryState'
import {sessionState, sessionWriter} from '@/util/sessionState'

function isTransientError(error: unknown): boolean {
    const status = (error as {response?: {status?: number}})?.response?.status
    return status == null || status >= 500
}

/**
 * Lifecycle of the signed-in session plus the account and station state derived from it.
 * Permission checks live in {@link usePermissions} and are composed in here so call sites
 * keep a single entry point.
 *
 * <p>Take it at the top of a setup or a composable, before anything is awaited: the session is
 * held per request. The load counter is held with it, so that an answer to a load that a later
 * load or a sign-out overtook is dropped.
 */
export function useSession() {
    const state = sessionState()
    const writer = sessionWriter()
    const acting = actingStationState()
    const loadSeq = useState('useSession.loadSeq', () => 0)

    async function load() {
        const seq = ++loadSeq.value
        const requestedStation = getItem('station_id')
        const requestedCluster = getItem('cluster_id')
        for (let attempt = 1; ; attempt++) {
            try {
                const info = await session.getSessionInfo()
                if (seq !== loadSeq.value) return
                writer.setInfo(info)
                writer.setLoadFailed(false)
                break
            } catch (error) {
                if (seq !== loadSeq.value) return
                if (!isTransientError(error) || attempt >= 3) {
                    writer.setInfo(null)
                    writer.setLoadFailed(true)
                    break
                }
                await new Promise(resolve => setTimeout(resolve, 500 * attempt))
            }
        }
        writer.setContext(requestedStation, requestedCluster)
        writer.setLoaded(true)
        claimVisitedArea()
    }

    function clear() {
        loadSeq.value++
        writer.setInfo(null)
        writer.setLoaded(false)
        writer.setLoadFailed(false)
        writer.setContext(null, null)
    }

    /**
     * The name to put on screen for whoever is signed in.
     *
     * The station resolves it, because what somebody is called belongs to their membership there
     * and not to their account. Where there is no membership, as on an instance-wide screen, the
     * register name is all there is.
     */
    function fullName(): string {
        const called = state.value.info?.member?.calledName
        if (called) return called
        const account = state.value.info?.account
        if (!account) return ''
        return [account.firstName, account.lastName].filter(Boolean).join(' ')
    }

    function isKbPublic(): boolean {
        const mode = state.value.info?.publicKbMode
        return mode != null && mode !== 'OFF'
    }

    /**
     * The clock the current station keeps its days by.
     *
     * <p>Any day used to look something up is the station's day and not the reader's: the server
     * files an appointment's sign-ups, absences and claimed gear under the date the appointment
     * falls on where the station stands. Hand this to {@link stationDayOf} rather than reading a
     * day off the reader's own clock.
     */
    const stationTimezone = computed(() => state.value.info?.stationTimezone ?? null)

    return {
        sessionInfo: computed(() => state.value.info),
        stationTimezone,
        loaded: computed(() => state.value.loaded),
        loadFailed: computed(() => state.value.loadFailed),
        sessionStationId: computed(() => state.value.stationId),
        sessionClusterId: computed(() => state.value.clusterId),
        /** The station an association's screen acts at right now, or null outside such a screen. */
        actingStation: acting.current,
        load,
        clear,
        fullName,
        isKbPublic,
        ...usePermissions(),
    }
}
