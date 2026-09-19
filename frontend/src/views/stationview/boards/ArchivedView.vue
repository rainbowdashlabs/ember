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
import type { Board, BoardTicket, BoardLabel } from '@/api/boards'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import LabelChipFilter from './archivedview/LabelChipFilter.vue'
import TicketTable from './tickettable/TicketTable.vue'
import { useTicketTable } from './tickettable/useTicketTable'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const boardKey = computed(() => route.params.boardKey as string)
const board = ref<Board | null>(null)
const tickets = ref<BoardTicket[]>([])
const members = ref<MemberCompletion[]>([])
const allLabels = ref<BoardLabel[]>([])
const ticketLabelMap = ref<Map<number, number[]>>(new Map())
const labelFilter = ref<Set<number>>(new Set())

const {loading, error} = useAsyncLoader(async () => {
    const [b, lns, tks, m, lb, tlm] = await Promise.all([
        boards.getBoard(boardKey.value), boards.getLanes(boardKey.value), boards.listTickets(boardKey.value),
        stationMembers.listCompletions(), boards.getLabels(boardKey.value), boards.getAllTicketLabels(boardKey.value),
    ])
    board.value = b; members.value = m; allLabels.value = lb
    const map = new Map<number, number[]>()
    for (const { ticketId, labelId } of tlm) { if (!map.has(ticketId)) map.set(ticketId, []); map.get(ticketId)!.push(labelId) }
    ticketLabelMap.value = map
    const visibleLanes = lns.filter(l => !b.backlogLaneId || l.id !== b.backlogLaneId)
    const lastLane = visibleLanes.length > 0 ? visibleLanes[visibleLanes.length - 1] : null
    if (lastLane && b.hideDoneAfterDays > 0) {
        const cutoff = new Date(); cutoff.setDate(cutoff.getDate() - b.hideDoneAfterDays)
        tickets.value = tks.filter(tk => tk.laneId === lastLane.id && new Date(tk.laneEnteredAt) < cutoff)
    }
})

const filteredTickets = computed(() => {
    if (labelFilter.value.size === 0) return tickets.value
    return tickets.value.filter(t => { const ids = ticketLabelMap.value.get(t.id) ?? []; return ids.some(id => labelFilter.value.has(id)) })
})

const labelsByTicket = computed(() => new Map([...ticketLabelMap.value].map(([ticketId, ids]) => {
    const worn = new Set(ids)
    return [ticketId, allLabels.value.filter(label => worn.has(label.id))]
})))

function labelsForTicket(ticket: BoardTicket): BoardLabel[] {
    return labelsByTicket.value.get(ticket.id) ?? []
}

const table = useTicketTable({
    id: 'board-archived',
    shortKey: () => board.value?.shortKey ?? '',
    tickets: filteredTickets,
    members,
    labelsOf: labelsForTicket,
})
</script>

<template>
    <ViewContent
        :title="t('pages.board-archived.title')"
        :subtitle="t('pages.board-archived.subtitle')"
    >
        <Spinner v-if="loading" />
        <Alert v-else-if="error" variant="error">{{ error }}</Alert>
        <template v-else-if="board">
            <div class="flex items-center gap-3 mb-4">
                <IconButton :icon="['fas', 'chevron-left']" label="Back" @click="router.push(`/station/boards/${board.shortKey}`)" />
                <SectionHeader>{{ board.name }} - {{ t('boards.archived') }}</SectionHeader>
            </div>

            <LabelChipFilter v-if="allLabels.length > 0" v-model="labelFilter" :labels="allLabels" class="mb-4" />

            <EmptyState v-if="filteredTickets.length === 0">{{ t('boards.noTickets') }}</EmptyState>
            <TicketTable v-else :labels-of="labelsForTicket" :short-key="board.shortKey" :table="table" />
        </template>
    </ViewContent>
</template>
