/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed} from 'vue'
import {SidebarMode} from '@/api/generated/schema'
import {setSidebarMode} from '@/api/session'
import {useSession} from './useSession'

/**
 * How the sidebar of the signed-in account lists its sections: one at a time, or every section at once
 * as a tree that opens in place. The choice is kept with the account and comes with the session, so it
 * holds on every device; changing it saves it and loads the session again.
 *
 * <p>Take it at the top of a setup, before anything is awaited, as the session it reads is held per
 * request.
 */
export function useSidebarMode() {
    const {sessionInfo, load} = useSession()

    const mode = computed<SidebarMode>(() => sessionInfo.value?.account.sidebar ?? SidebarMode.SECTIONS)
    const tree = computed(() => mode.value === SidebarMode.TREE)

    async function choose(next: SidebarMode) {
        await setSidebarMode(next)
        await load()
    }

    return {mode, tree, choose}
}
