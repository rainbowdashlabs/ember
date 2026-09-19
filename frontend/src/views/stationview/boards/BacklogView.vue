/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import IconButton from '@/components/button/IconButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import { boards, stationMembers } from '@/api'
import type { MemberCompletion } from '@/api/stationMembers'
import type { Board, BoardTicket } from '@/api/boards'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import TicketTable from './tickettable/TicketTable.vue'
import { useTicketTable } from './tickettable/useTicketTable'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const boardKey = computed(() => route.params.boardKey as string)
const board = ref<Board | null>(null)
const tickets = ref<BoardTicket[]>([])
const members = ref<MemberCompletion[]>([])

const {loading, error} = useAsyncLoader(async () => {
    const [b, t, m] = await Promise.all([boards.getBoard(boardKey.value), boards.listTickets(boardKey.value), stationMembers.listCompletions()])
    board.value = b
    members.value = m
    if (b.backlogLaneId) {
        tickets.value = t.filter(tk => tk.laneId === b.backlogLaneId)
    }
})

const table = useTicketTable({
    id: 'board-backlog',
    shortKey: () => board.value?.shortKey ?? '',
    tickets,
    members,
})
</script>

<template>
    <ViewContent
        :title="t('pages.board-backlog.title')"
        :subtitle="t('pages.board-backlog.subtitle')"
    >
        <Spinner v-if="loading" />
        <Alert v-else-if="error" variant="error">{{ error }}</Alert>
        <template v-else-if="board">
            <div class="flex items-center gap-3 mb-6">
                <IconButton :icon="['fas', 'chevron-left']" label="Back" @click="router.push(`/station/boards/${board.shortKey}`)" />
                <SectionHeader>{{ board.name }} - {{ t('boards.backlogTitle') }}</SectionHeader>
            </div>

            <EmptyState v-if="tickets.length === 0">{{ t('boards.noTickets') }}</EmptyState>
            <TicketTable v-else :short-key="board.shortKey" :table="table" />
        </template>
    </ViewContent>
</template>
