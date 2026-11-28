/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {onBeforeUnmount, onMounted, watch} from 'vue'
import {useRoute} from 'vue-router'
import {changesMade} from '@/util/countRefreshState'

/** How long a run of writes is waited out before the counts are read once for all of them. */
export const WRITE_SETTLE_MS = 300

/** How often the counts are read while the tab is in view, to catch what other people changed. */
export const POLL_MS = 60_000

/**
 * Keeps an area's sidebar counters following the lists behind them, without a page reload.
 *
 * <p>The counts are read again after the reader's own writes (waited out by {@link WRITE_SETTLE_MS},
 * so a batch costs one read), on every move to another page of the area, when the tab comes back into
 * view, and every {@link POLL_MS} while it is in view. A hidden tab reads nothing; it catches up the
 * moment it is shown. Used once per area, by the shell that draws its sidebar.
 *
 * @param refresh reads the area's counts again
 * @param active  whether the counts may be read now, such as once the session is known
 */
export function useLiveCounts(refresh: () => unknown, active: () => boolean = () => true) {
    const route = useRoute()
    let settling: ReturnType<typeof setTimeout> | null = null
    let polling: ReturnType<typeof setInterval> | null = null

    function readNow() {
        if (!active() || document.visibilityState === 'hidden') return
        void refresh()
    }

    function afterWrites() {
        if (settling) clearTimeout(settling)
        settling = setTimeout(() => {
            settling = null
            readNow()
        }, WRITE_SETTLE_MS)
    }

    function onVisibility() {
        if (document.visibilityState === 'visible') readNow()
    }

    watch(changesMade(), afterWrites)
    watch(() => route.path, readNow)

    onMounted(() => {
        document.addEventListener('visibilitychange', onVisibility)
        polling = setInterval(readNow, POLL_MS)
    })

    onBeforeUnmount(() => {
        document.removeEventListener('visibilitychange', onVisibility)
        if (polling) clearInterval(polling)
        if (settling) clearTimeout(settling)
    })
}
