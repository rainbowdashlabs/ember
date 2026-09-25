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
import FailureAlert from '@/components/feedback/FailureAlert.vue'
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

const {loading, failure} = useAsyncLoader(async () => {
    const [b, t, m] = await Promise.all([boards.getBoard(boardKey.value), boards.listTickets(boardKey.value), stationMembers.listCompletions()])
    board.value = b
    members.value = m
    if (b.backlogLaneId) {
        tickets.value = t.filter(tk => tk.laneId === b.backlogLaneId)
    }
})

/**
 * Which board's backlog is open, by the short key the reader already knows from the ticket numbers.
 * Three boards otherwise leave three tabs all reading "Backlog". The word alone stands until the
 * board has arrived, and where it could not be fetched at all.
 */
const pageTitle = computed(() => board.value
    ? t('pages.board-backlog.titleNamed', {name: board.value.shortKey})
    : t('pages.board-backlog.title'))

/** The short key names the board above; the line under it spells the board out. */
const pageSubtitle = computed(() => board.value?.name || t('pages.board-backlog.subtitle'))

const table = useTicketTable({
    id: 'board-backlog',
    shortKey: () => board.value?.shortKey ?? '',
    tickets,
    members,
})
</script>

<template>
    <ViewContent
        :title="pageTitle"
        :subtitle="pageSubtitle"
    >
        <Spinner v-if="loading" />
        <FailureAlert v-else-if="failure" :failure="failure"/>
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
