/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref} from 'vue'

export type QuickSearchScope = 'station' | 'admin' | 'cluster'

const isOpen = ref(false)
const scope = ref<QuickSearchScope>('station')

/**
 * Global singleton controlling the Ctrl+K command palette.
 *
 * <p>The palette has two scope modes: {@code station} (shown inside the
 * station layout, with member / event / knowledge-base data sources) and
 * {@code admin} (shown inside the admin layout, page-routes only).
 */
export function useQuickSearch() {
    function open(nextScope: QuickSearchScope) {
        scope.value = nextScope
        isOpen.value = true
    }

    function close() {
        isOpen.value = false
    }

    function toggle(nextScope: QuickSearchScope) {
        if (isOpen.value) {
            close()
        } else {
            open(nextScope)
        }
    }

    return {isOpen, scope, open, close, toggle}
}
