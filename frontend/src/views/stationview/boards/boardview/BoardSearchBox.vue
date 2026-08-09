/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import SearchInput from '@/components/input/text/SearchInput.vue'
import BoardSearchResult from './BoardSearchResult.vue'
import { boards } from '@/api'
import type { BoardLabel, BoardTicket } from '@/api/boards'

const props = defineProps<{
    boardKey: string
    shortKey: string
    labelsForTicket: (ticketId: number) => BoardLabel[]
    laneName: (laneId: number) => string
}>()

const emit = defineEmits<{
    select: [ticket: BoardTicket]
}>()

const { t } = useI18n()

const searchQuery = ref('')
const searchResults = ref<BoardTicket[] | null>(null)

let searchTimeout: ReturnType<typeof setTimeout> | null = null

function onSearchInput() {
    if (searchTimeout) clearTimeout(searchTimeout)
    if (!searchQuery.value.trim()) {
        searchResults.value = null
        return
    }
    searchTimeout = setTimeout(async () => {
        try {
            searchResults.value = await boards.searchTickets(props.boardKey, searchQuery.value.trim())
        } catch { void 0 }
    }, 300)
}

function select(ticket: BoardTicket) {
    emit('select', ticket)
    searchQuery.value = ''
    searchResults.value = null
}
</script>

<template>
    <div class="relative">
        <SearchInput v-model="searchQuery" :placeholder="t('boards.searchTickets')" class="w-96" @input="onSearchInput" />
        <div v-if="searchResults && searchResults.length > 0" class="absolute z-20 mt-1 w-[28rem] right-0 rounded-theme border border-(--border) bg-(--bg) shadow-lg overflow-hidden">
            <BoardSearchResult
                v-for="result in searchResults"
                :key="result.id"
                :result="result"
                :short-key="shortKey"
                :labels="labelsForTicket(result.id)"
                :lane-name="laneName(result.laneId)"
                @select="select(result)"
            />
        </div>
    </div>
</template>
