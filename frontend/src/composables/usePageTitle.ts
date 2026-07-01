/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {watch} from 'vue'
import {usePageHeader} from '@/composables/usePageHeader'

/**
 * Mirrors the shared page header state into the browser tab title. The header
 * itself is populated by whichever {@code ViewContent} instance is currently
 * mounted (via its {@code title} prop).
 */
export function usePageTitle() {
    if (typeof window === 'undefined') return

    const {title} = usePageHeader()

    watch(title, (val) => {
        useHead({title: val || undefined})
    }, {immediate: true})
}
