/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import KanbanBoard from '@/components/kanban/KanbanBoard.vue'
import { boardLanes } from '@/components/kanban/kanbanLanes'
import BoardHeaderBar from './boardview/BoardHeaderBar.vue'
import BoardFilterBar from './boardview/BoardFilterBar.vue'
import BoardCreateTicketModal from './boardview/BoardCreateTicketModal.vue'
import { useTicketMoves } from '@/composables/useTicketMoves'
import { boards, stationMembers } from '@/api'
import type { Board, BoardLabel, BoardLane, MemberCompletion, TicketSummary } from '@/api/generated/schema'
import { useSession } from '@/composables/useSession'
import { useAsyncLoader } from '@/composables/useAsyncLoader'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const { sessionInfo } = useSession()

const boardKey = computed(() => route.params.boardKey as string)
const board = ref<Board | null>(null)
const lanes = ref<BoardLane[]>([])
const tickets = ref<TicketSummary[]>([])

const assigneeFilter = ref<Set<string>>(new Set())
const labelFilter = ref<string[]>([])
const allLabels = ref<BoardLabel[]>([])
const ticketLabelMap = ref<Map<number, number[]>>(new Map())
const showCreateModal = ref(false)

const members = ref<MemberCompletion[]>([])
const assignableMembers = ref<MemberCompletion[]>([])
const {loading, failure, reload} = useAsyncLoader(async (isCurrent) => {
    const [b, l, t, m, am, lb, tlm] = await Promise.all([
        boards.getBoard(boardKey.value),
        boards.getLanes(boardKey.value),
        boards.listTickets(boardKey.value),
        stationMembers.listCompletions(),
        boards.getAssignableMembers(boardKey.value),
        boards.getLabels(boardKey.value),
        boards.getAllTicketLabels(boardKey.value),
    ])
    if (!isCurrent()) return
    board.value = b
    lanes.value = l
    tickets.value = t
    members.value = m
    assignableMembers.value = am
    allLabels.value = lb
    const map = new Map<number, number[]>()
    for (const { ticketId, labelId } of tlm) { if (!map.has(ticketId)) map.set(ticketId, []); map.get(ticketId)!.push(labelId) }
    ticketLabelMap.value = map
})

/**
 * The board's own name at the head of the page, because "Board" is the same word above every one of
 * them and it is what the tab, the history and a bookmark end up carrying. The word stands until the
 * board has arrived, and where it could not be fetched at all.
 */
const pageTitle = computed(() => board.value?.name || t('pages.board-view.title'))

/** Once the name is the title, the line under it is where the board says what it is for. */
const pageSubtitle = computed(() => board.value?.description || t('pages.board-view.subtitle'))

const visibleLanes = computed(() => boardLanes(lanes.value, board.value?.backlogLaneId ?? null))
const backlogLane = computed(() => board.value?.backlogLaneId ? lanes.value.find(l => l.id === board.value!.backlogLaneId) ?? null : null)

/** Whether a ticket passes the assignee and label filters; an empty filter lets everything through. */
function matchesFilters(ticket: TicketSummary): boolean {
    if (assigneeFilter.value.size > 0 && !(ticket.assignee?.memberUid && assigneeFilter.value.has(ticket.assignee.memberUid))) {
        return false
    }
    if (labelFilter.value.length === 0) return true
    const filterIds = new Set(labelFilter.value.map(Number))
    return (ticketLabelMap.value.get(ticket.id) ?? []).some(id => filterIds.has(id))
}

function labelsForTicket(ticketId: number): BoardLabel[] {
    const ids = ticketLabelMap.value.get(ticketId) ?? []
    return allLabels.value.filter(l => ids.includes(l.id))
}

const createLaneOptions = computed(() => {
    const options: BoardLane[] = []
    if (backlogLane.value) options.push(backlogLane.value)
    const firstVisible = visibleLanes.value[0]
    if (firstVisible) options.push(firstVisible)
    return options
})

const defaultCreateLaneId = computed(() => {
    const current = board.value
    if (!current) return null
    return current.backlogLaneId ?? lanes.value.find(l => l.id !== current.backlogLaneId)?.id ?? null
})

const assignees = computed(() => {
    const uids = new Set(tickets.value.map(t => t.assignee?.memberUid).filter(Boolean) as string[])
    const list = members.value.filter(m => uids.has(m.memberUid))
    const i = list.findIndex(m => m.memberUid === sessionInfo.value?.member?.uid)
    if (i <= 0) return list
    const [self] = list.splice(i, 1)
    return self ? [self, ...list] : list
})

function openTicketDetail(ticket: TicketSummary) {
    router.push(`/station/boards/${boardKey.value}/tickets/${ticket.ticketNumber}`)
}

function laneName(laneId: number): string {
    return lanes.value.find(l => l.id === laneId)?.name ?? ''
}

const {moveTicket} = useTicketMoves(tickets, {
    reorder: (ticketNumber, payload) => boards.reorderTickets(boardKey.value, ticketNumber, payload),
    move: (ticketNumber, payload) => boards.moveTicket(boardKey.value, ticketNumber, payload),
}, reload)

watch(boardKey, reload)
</script>

<template>
    <ViewContent
        :title="pageTitle"
        :subtitle="pageSubtitle"
    >
        <Spinner v-if="loading" />
        <FailureAlert v-else-if="failure" :failure="failure"/>
        <template v-else-if="board">
            <BoardHeaderBar
                :board-key="boardKey"
                :short-key="board.shortKey"
                :labels-for-ticket="labelsForTicket"
                :lane-name="laneName"
                @create="showCreateModal = true"
            />

            <BoardFilterBar
                v-model:assignee-filter="assigneeFilter"
                v-model:label-filter="labelFilter"
                :short-key="board.shortKey"
                :has-backlog="backlogLane !== null"
                :assignees="assignees"
                :labels="allLabels"
            />

            <KanbanBoard
                :board="board"
                :lanes="lanes"
                :tickets="tickets"
                :filter="matchesFilters"
                :members="members"
                :labels-for-ticket="labelsForTicket"
                archive-linked
                @move="moveTicket"
                @open="openTicketDetail"
                @open-archive="router.push(`/station/boards/${board.shortKey}/archived`)"
            />

            <BoardCreateTicketModal
                v-model="showCreateModal"
                :board-key="boardKey"
                :short-key="board.shortKey"
                :lane-options="createLaneOptions"
                :default-lane-id="defaultCreateLaneId"
                :assignable-members="assignableMembers"
            />
        </template>
    </ViewContent>
</template>
