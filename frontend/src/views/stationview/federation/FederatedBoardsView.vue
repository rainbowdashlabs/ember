/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import FederatedBoardStationGroup from './federatedboardsview/FederatedBoardStationGroup.vue'
import {useSession} from '@/composables/useSession'
import {
    type DiscoveredBoard,
    type FederatedBoardBookmark,
    discoverBoards,
    listBookmarks,
    createBookmark,
    deleteBookmark,
} from '@/api/federatedBoards'
import {useFederatedBoardBookmarks} from '@/composables/useFederatedBoardBookmarks'
import {useAsyncLoader} from '@/composables/useAsyncLoader'

const {t} = useI18n()
const router = useRouter()
const {loaded} = useSession()
const {refresh: refreshSidebarBookmarks} = useFederatedBoardBookmarks()

const boards = ref<DiscoveredBoard[]>([])
const bookmarks = ref<FederatedBoardBookmark[]>([])
const togglingBookmark = ref<string | null>(null)

const groupedBoards = computed(() => {
    const groups = new Map<string, DiscoveredBoard[]>()
    for (const board of boards.value) {
        const key = board.partnerStationName
        if (!groups.has(key)) {
            groups.set(key, [])
        }
        groups.get(key)!.push(board)
    }
    return groups
})

function bookmarkKey(partnerStationUid: string, remoteBoardUid: string): string {
    return `${partnerStationUid}:${remoteBoardUid}`
}

function findBookmark(partnerStationUid: string, remoteBoardUid: string): FederatedBoardBookmark | undefined {
    return bookmarks.value.find(b => b.partnerStationUid === partnerStationUid && b.remoteBoardUid === remoteBoardUid)
}

function isBookmarked(partnerStationUid: string, remoteBoardUid: string): boolean {
    return !!findBookmark(partnerStationUid, remoteBoardUid)
}

const {loading, error, reload: loadData} = useAsyncLoader(async () => {
    const [discoveredBoards, existingBookmarks] = await Promise.all([
        discoverBoards(),
        listBookmarks(),
    ])
    boards.value = discoveredBoards
    bookmarks.value = existingBookmarks
}, {autoLoad: false})

async function toggleBookmark(board: DiscoveredBoard) {
    const key = bookmarkKey(board.partnerStationUid, board.remoteBoardUid)
    if (togglingBookmark.value === key) return
    togglingBookmark.value = key
    try {
        const existing = findBookmark(board.partnerStationUid, board.remoteBoardUid)
        if (existing) {
            await deleteBookmark(existing.id)
            bookmarks.value = bookmarks.value.filter(b => b.id !== existing.id)
        } else {
            const created = await createBookmark({
                partnerUid: board.partnerStationUid,
                remoteBoardUid: board.remoteBoardUid,
                remoteBoardName: board.name,
                remoteBoardShortKey: board.shortKey,
                shareMode: board.shareMode,
            })
            bookmarks.value.push(created)
        }
        await refreshSidebarBookmarks()
    } catch {
        error.value = t('common.error')
    } finally {
        togglingBookmark.value = null
    }
}

function navigateToBoard(board: DiscoveredBoard) {
    router.push(`/station/federation/boards/${board.partnerStationUid}/${board.shortKey}`)
}

watch(loaded, (v) => {
    if (v) loadData()
}, {immediate: true})
</script>

<template>
    <ViewContent>
        <SectionHeader class="mb-4">{{ t('boards.federatedBoards') }}</SectionHeader>
        <MutedText size="sm" class="mb-4">{{ t('boards.federatedBoardsDesc') }}</MutedText>

        <Alert variant="info" class="mb-4">{{ t('boards.bookmarkHint') }}</Alert>
        <Alert v-if="error" variant="error" class="mb-4">{{ error }}</Alert>

        <Spinner v-if="loading"/>

        <template v-if="!loading && boards.length === 0">
            <MutedText>{{ t('boards.noFederatedBoards') }}</MutedText>
        </template>

        <template v-if="!loading && boards.length > 0">
            <div class="space-y-6">
                <FederatedBoardStationGroup
                    v-for="[stationName, stationBoards] in groupedBoards"
                    :key="stationName"
                    :station-name="stationName"
                    :station-boards="stationBoards"
                    :is-bookmarked="isBookmarked"
                    @toggle-bookmark="toggleBookmark"
                    @navigate="navigateToBoard"
                />
            </div>
        </template>
    </ViewContent>
</template>
