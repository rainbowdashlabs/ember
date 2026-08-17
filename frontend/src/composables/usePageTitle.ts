/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {usePageHeader} from '@/composables/usePageHeader'

/**
 * Mirrors the shared page header state into the browser tab title. The header
 * itself is populated by whichever {@code ViewContent} instance is currently
 * mounted (via its {@code title} prop). Registered once as a reactive head
 * binding - repeated {@code useHead} calls from a watcher would stack head
 * entries and keep the previous title alive when the header becomes empty.
 */
export function usePageTitle() {
    if (typeof window === 'undefined') return

    const {title} = usePageHeader()

    useHead({title: () => title.value || null})
}
