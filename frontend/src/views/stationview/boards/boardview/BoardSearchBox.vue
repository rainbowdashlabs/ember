/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import SearchInput from '@/components/input/text/SearchInput.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import BoardSearchResult from './BoardSearchResult.vue'
import { boards } from '@/api'
import type { BoardLabel, BoardTicket } from '@/api/boards'
import { describeFailure, type Failure } from '@/util/failure'

const props = defineProps<{
    boardKey: string
    shortKey: string
    labelsForTicket: (ticketId: number) => BoardLabel[]
    laneName: (laneId: number) => string
}>()

const { t } = useI18n()

const searchQuery = ref('')
const searchResults = ref<BoardTicket[] | null>(null)

let searchTimeout: ReturnType<typeof setTimeout> | null = null

/**
 * Why the search came back with nothing to show.
 *
 * <p>A failed search used to leave the list empty, which is indistinguishable from a board that holds
 * no such ticket. Somebody then creates the ticket a second time because the first one could not be
 * found.
 */
const failure = ref<Failure | null>(null)

function onSearchInput() {
    if (searchTimeout) clearTimeout(searchTimeout)
    failure.value = null
    if (!searchQuery.value.trim()) {
        searchResults.value = null
        return
    }
    searchTimeout = setTimeout(async () => {
        try {
            searchResults.value = await boards.searchTickets(props.boardKey, searchQuery.value.trim())
        } catch (e) {
            searchResults.value = null
            failure.value = {...describeFailure(e, t), message: t('boards.searchFailed')}
        }
    }, 300)
}
</script>

<template>
    <div class="relative">
        <SearchInput v-model="searchQuery" :placeholder="t('boards.searchTickets')" class="w-96" @input="onSearchInput" />
        <FailureAlert :failure="failure" class="mt-1"/>
        <div v-if="searchResults && searchResults.length > 0" class="absolute z-20 mt-1 w-[28rem] right-0 rounded-theme border border-(--border) bg-(--bg) shadow-lg overflow-hidden">
            <BoardSearchResult
                v-for="result in searchResults"
                :key="result.id"
                :result="result"
                :short-key="shortKey"
                :labels="labelsForTicket(result.id)"
                :lane-name="laneName(result.laneId)"
            />
        </div>
    </div>
</template>
