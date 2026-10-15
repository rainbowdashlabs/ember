/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref, type Ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useSession} from '@/composables/useSession'
import {useSidebarCounts} from '@/composables/useSidebarCounts'
import {useSidebarBoards} from '@/composables/useSidebarBoards'
import {useFederatedBoardBookmarks} from '@/composables/useFederatedBoardBookmarks'
import {useStationTransferStatus} from '@/composables/useStationTransferStatus'
import {MOVED_STATION_NAV, STATION_NAV, type SetupNavStep} from '@/data/sidebar/stationNav'
import {resolveNav} from '@/util/navTree'

/**
 * The station sidebar as the reader sees it, which the sidebar renders and the palette searches.
 *
 * <p>A station that has moved away shows only what is left to do about the move.
 *
 * @param setupSteps the steps of the station's first setup, which only the sidebar lists
 */
export function useStationNav(setupSteps: Ref<SetupNavStep[]> = ref([])) {
    const {t} = useI18n()
    const session = useSession()
    const {counts} = useSidebarCounts()
    const {boards} = useSidebarBoards()
    const {bookmarks} = useFederatedBoardBookmarks()
    const {hasMoved} = useStationTransferStatus()

    const nodes = computed(() => resolveNav(hasMoved.value ? MOVED_STATION_NAV : STATION_NAV, {
        session,
        counts: counts.value,
        boards: boards.value,
        boardBookmarks: bookmarks.value,
        setupSteps: setupSteps.value,
    }, t))

    return {nodes}
}
