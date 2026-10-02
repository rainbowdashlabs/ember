/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {readonly, type DeepReadonly, type Ref} from 'vue'
import type {SessionInfo} from '@/api/generated/schema'

/** The signed-in session as this page last heard it. */
export interface SessionState {
    info: SessionInfo | null
    loaded: boolean
    loadFailed: boolean
    /** The station the loaded session was answered for. */
    stationId: string | null
    /**
     * The cluster the loaded session was answered for. A session fetched without one carries no cluster
     * role and no cluster permissions, so a shell that opens on a cluster has to know whether what it
     * holds was asked for that cluster or for none.
     */
    clusterId: string | null
}

/**
 * The session, held per request. {@code useSession} is the only writer; {@code usePermissions}, the
 * session-driven theme sync and the landing memory are readers. Keeping it in its own module lets those
 * readers stay free of an import cycle back into the session composable.
 */
function heldSession(): Ref<SessionState> {
    return useState<SessionState>('sessionState', () => ({
        info: null,
        loaded: false,
        loadFailed: false,
        stationId: null,
        clusterId: null,
    }))
}

/**
 * The session, read-only. Take it at the top of a setup or a composable, before anything is awaited.
 */
export function sessionState(): Readonly<Ref<DeepReadonly<SessionState>>> {
    return readonly(heldSession())
}

/**
 * The operations that change the session, bound to the request that asks for them. Take them at the
 * top of a setup or a composable, before anything is awaited.
 */
export function sessionWriter() {
    const state = heldSession()
    return {
        /** Keeps what the server said about the session, or that it said nothing. */
        setInfo(info: SessionInfo | null): void {
            state.value.info = info
        },
        setLoaded(loaded: boolean): void {
            state.value.loaded = loaded
        },
        setLoadFailed(failed: boolean): void {
            state.value.loadFailed = failed
        },
        /** Notes the station and cluster the loaded session was asked for. */
        setContext(stationId: string | null, clusterId: string | null): void {
            state.value.stationId = stationId
            state.value.clusterId = clusterId
        },
    }
}
