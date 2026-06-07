/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import IconButton from '@/components/button/IconButton.vue'
import {useSession} from '@/composables/useSession'
import {
    type DiscoveredBoard,
    type FederatedBoardBookmark,
    BoardShareMode,
    discoverBoards,
    listBookmarks,
    createBookmark,
    deleteBookmark,
} from '@/api/federatedBoards'
import {useFederatedBoardBookmarks} from '@/composables/useFederatedBoardBookmarks'

const {t} = useI18n()
const router = useRouter()
const {loaded} = useSession()
const {refresh: refreshSidebarBookmarks} = useFederatedBoardBookmarks()

const loading = ref(true)
const error = ref('')
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

function truncate(text: string, maxLength: number): string {
    if (text.length <= maxLength) return text
    return text.slice(0, maxLength) + '...'
}

async function loadData() {
    loading.value = true
    error.value = ''
    try {
        const [discoveredBoards, existingBookmarks] = await Promise.all([
            discoverBoards(),
            listBookmarks(),
        ])
        boards.value = discoveredBoards
        bookmarks.value = existingBookmarks
    } catch {
        error.value = t('common.error')
    } finally {
        loading.value = false
    }
}

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

onMounted(() => {
    if (loaded.value) loadData()
})
watch(loaded, (v) => {
    if (v) loadData()
})
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
                <div v-for="[stationName, stationBoards] in groupedBoards" :key="stationName">
                    <SubHeader class="mb-3">{{ stationName }}</SubHeader>
                    <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                        <NeutralContainer
                            v-for="board in stationBoards"
                            :key="bookmarkKey(board.partnerStationUid, board.remoteBoardUid)"
                            class="cursor-pointer hover:ring-2 hover:ring-primary transition-all"
                            @click="navigateToBoard(board)"
                        >
                            <div class="flex items-start justify-between gap-2">
                                <div class="min-w-0 flex-1">
                                    <div class="flex items-center gap-2 flex-wrap">
                                        <span class="font-semibold">{{ board.name }}</span>
                                        <code class="text-xs font-mono bg-bg-light-accent dark:bg-bg-dark-accent px-1.5 py-0.5 rounded">{{ board.shortKey }}</code>
                                    </div>
                                    <p v-if="board.description" class="text-sm mt-1 text-text-light-secondary dark:text-text-dark-secondary">
                                        {{ truncate(board.description, 120) }}
                                    </p>
                                </div>
                                <IconButton
                                    :icon="isBookmarked(board.partnerStationUid, board.remoteBoardUid) ? ['fas', 'star'] : ['fas', 'star']"
                                    :label="isBookmarked(board.partnerStationUid, board.remoteBoardUid) ? t('boards.bookmarked') : t('boards.bookmark')"
                                    :class="isBookmarked(board.partnerStationUid, board.remoteBoardUid)
                                        ? 'text-yellow-500 hover:text-yellow-600'
                                        : 'text-text-light-secondary dark:text-text-dark-secondary hover:text-yellow-500'"
                                    @click.stop="toggleBookmark(board)"
                                />
                            </div>
                            <div class="flex items-center gap-2 mt-3">
                                <span
                                    v-if="board.shareMode === BoardShareMode.READ_ONLY"
                                    class="inline-flex items-center gap-1 text-xs px-2 py-0.5 rounded-full bg-bg-light-accent dark:bg-bg-dark-accent"
                                >
                                    <font-awesome-icon :icon="['fas', 'lock']" class="text-[0.65rem]"/>
                                    {{ t('boards.readOnlyBadge') }}
                                </span>
                                <span
                                    v-else-if="board.shareMode === BoardShareMode.FULL"
                                    class="inline-flex items-center gap-1 text-xs px-2 py-0.5 rounded-full bg-green-100 dark:bg-green-900 text-green-800 dark:text-green-200"
                                >
                                    <font-awesome-icon :icon="['fas', 'pen']" class="text-[0.65rem]"/>
                                    {{ t('boards.fullAccessBadge') }}
                                </span>
                            </div>
                        </NeutralContainer>
                    </div>
                </div>
            </div>
        </template>
    </ViewContent>
</template>
