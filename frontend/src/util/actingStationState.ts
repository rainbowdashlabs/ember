/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref} from 'vue'

/**
 * The station a request should be answered for, when it is not the one the reader last picked.
 *
 * <p>An association keeps its knowledge base, its news and its calendar on the station it owns, and the
 * screens that edit them are the station's own screens. While one of those screens is open, the station
 * every request means is that one, whether or not the reader also belongs to a station of their own. What
 * the reader may do there is decided the same way, by what they hold at the association.
 *
 * <p>Set by {@code useActingStation}, read by the request client and by the permission checks, and nothing
 * else touches it. It is a ref so that a check made once, in a computed, notices when it changes.
 */
const actingStationUid = ref<string | null>(null)

export function setActingStation(uid: string | null): void {
    actingStationUid.value = uid
}

export function getActingStation(): string | null {
    return actingStationUid.value
}
