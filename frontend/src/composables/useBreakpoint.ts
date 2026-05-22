/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { ref, onMounted, onUnmounted } from 'vue'

const MD_BREAKPOINT = 768

const isMobile = ref(typeof window !== 'undefined' ? window.innerWidth < MD_BREAKPOINT : false)

let listenerCount = 0

function updateBreakpoint() {
    isMobile.value = window.innerWidth < MD_BREAKPOINT
}

export function useBreakpoint() {
    onMounted(() => {
        if (listenerCount === 0) {
            window.addEventListener('resize', updateBreakpoint)
        }
        listenerCount++
        updateBreakpoint()
    })

    onUnmounted(() => {
        listenerCount--
        if (listenerCount === 0) {
            window.removeEventListener('resize', updateBreakpoint)
        }
    })

    return { isMobile }
}
