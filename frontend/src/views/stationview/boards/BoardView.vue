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
import Alert from '@/components/feedback/Alert.vue'
import KanbanLane from './boardview/KanbanLane.vue'
import BoardHeaderBar from './boardview/BoardHeaderBar.vue'
import BoardFilterBar from './boardview/BoardFilterBar.vue'
import BoardCreateTicketModal from './boardview/BoardCreateTicketModal.vue'
import { useBoardDragAndDrop } from '@/composables/useBoardDragAndDrop'
import { boards, stationMembers } from '@/api'
import type { MemberCompletion } from '@/api/stationMembers'
import type { Board, BoardLane, BoardTicket, BoardLabel } from '@/api/boards'
import { useSession } from '@/composables/useSession'
import { useAsyncLoader } from '@/composables/useAsyncLoader'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const { sessionInfo } = useSession()

const boardKey = computed(() => route.params.boardKey as string)
const board = ref<Board | null>(null)
const lanes = ref<BoardLane[]>([])
const tickets = ref<BoardTicket[]>([])

const assigneeFilter = ref<Set<string>>(new Set())
const labelFilter = ref<string[]>([])
const allLabels = ref<BoardLabel[]>([])
const ticketLabelMap = ref<Map<number, number[]>>(new Map())
const showCreateModal = ref(false)

const members = ref<MemberCompletion[]>([])
const {loading, error, reload} = useAsyncLoader(async () => {
    const [b, l, t, m, lb, tlm] = await Promise.all([
        boards.getBoard(boardKey.value),
        boards.getLanes(boardKey.value),
        boards.listTickets(boardKey.value),
        stationMembers.listCompletions(),
        boards.getLabels(boardKey.value),
        boards.getAllTicketLabels(boardKey.value),
    ])
    board.value = b
    lanes.value = l
    tickets.value = t
    members.value = m
    allLabels.value = lb
    const map = new Map<number, number[]>()
    for (const { ticketId, labelId } of tlm) { if (!map.has(ticketId)) map.set(ticketId, []); map.get(ticketId)!.push(labelId) }
    ticketLabelMap.value = map
})

const visibleLanes = computed(() => lanes.value.filter(l => !board.value?.backlogLaneId || l.id !== board.value.backlogLaneId))
const backlogLane = computed(() => board.value?.backlogLaneId ? lanes.value.find(l => l.id === board.value!.backlogLaneId) ?? null : null)

function ticketsForLane(laneId: number): BoardTicket[] {
    let filtered = tickets.value.filter(t => t.laneId === laneId)
    if (assigneeFilter.value.size > 0) {
        filtered = filtered.filter(t => !!t.assignee?.memberUid && assigneeFilter.value.has(t.assignee.memberUid))
    }
    if (labelFilter.value.length > 0) {
        const filterIds = new Set(labelFilter.value.map(Number))
        filtered = filtered.filter(t => { const ids = ticketLabelMap.value.get(t.id) ?? []; return ids.some(id => filterIds.has(id)) })
    }
    return filtered.sort((a, b) => a.position - b.position)
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

function isLastLane(laneId: number): boolean {
    const vl = visibleLanes.value
    return vl[vl.length - 1]?.id === laneId
}

function shouldHideTicket(ticket: BoardTicket, laneId: number): boolean {
    if (!isLastLane(laneId)) return false
    if (!board.value) return false
    const entered = new Date(ticket.laneEnteredAt)
    const cutoff = new Date()
    cutoff.setDate(cutoff.getDate() - board.value.hideDoneAfterDays)
    return entered < cutoff
}

function visibleTicketsForLane(laneId: number): BoardTicket[] {
    return ticketsForLane(laneId).filter(t => !shouldHideTicket(t, laneId))
}

function archivedCountForLane(laneId: number): number {
    return ticketsForLane(laneId).filter(t => shouldHideTicket(t, laneId)).length
}

const assignees = computed(() => {
    const uids = new Set(tickets.value.map(t => t.assignee?.memberUid).filter(Boolean) as string[])
    const list = members.value.filter(m => uids.has(m.memberUid))
    const i = list.findIndex(m => m.memberUid === sessionInfo.value?.member?.uid)
    if (i <= 0) return list
    const [self] = list.splice(i, 1)
    return self ? [self, ...list] : list
})

function openTicketDetail(ticket: BoardTicket) {
    router.push(`/station/boards/${boardKey.value}/tickets/${ticket.ticketNumber}`)
}

function laneName(laneId: number): string {
    return lanes.value.find(l => l.id === laneId)?.name ?? ''
}

const {
    dragTicket,
    dropLaneId,
    dropPosition,
    onTicketDragStart,
    onLaneDragOver,
    onLaneDragLeave,
    onLaneDrop,
    onDragEnd,
} = useBoardDragAndDrop(tickets, {
    reorder: (ticketNumber, payload) => boards.reorderTickets(boardKey.value, ticketNumber, payload),
    move: (ticketNumber, payload) => boards.moveTicket(boardKey.value, ticketNumber, payload),
}, reload)

watch(boardKey, reload)
</script>

<template>
    <ViewContent
        :title="t('pages.board-view.title')"
        :subtitle="t('pages.board-view.subtitle')"
    >
        <Spinner v-if="loading" />
        <Alert v-else-if="error" variant="error">{{ error }}</Alert>
        <template v-else-if="board">
            <BoardHeaderBar
                :board-key="boardKey"
                :short-key="board.shortKey"
                :labels-for-ticket="labelsForTicket"
                :lane-name="laneName"
                @create="showCreateModal = true"
                @open-ticket="openTicketDetail"
            />

            <BoardFilterBar
                v-model:assignee-filter="assigneeFilter"
                v-model:label-filter="labelFilter"
                :short-key="board.shortKey"
                :has-backlog="backlogLane !== null"
                :assignees="assignees"
                :labels="allLabels"
            />

            <div class="flex flex-col md:flex-row gap-4 md:overflow-x-auto pb-4" style="min-height: 200px">
                <KanbanLane
                    v-for="lane in visibleLanes"
                    :key="lane.id"
                    :lane="lane"
                    :tickets="visibleTicketsForLane(lane.id)"
                    :archived-count="archivedCountForLane(lane.id)"
                    :is-last-lane="isLastLane(lane.id)"
                    :drag-ticket="dragTicket"
                    :drop-lane-id="dropLaneId"
                    :drop-position="dropPosition"
                    :members="members"
                    :short-key="board.shortKey"
                    :labels-for-ticket="labelsForTicket"
                    @dragover="onLaneDragOver"
                    @dragleave="onLaneDragLeave"
                    @drop="onLaneDrop"
                    @ticket-dragstart="onTicketDragStart"
                    @ticket-dragend="onDragEnd"
                    @ticket-click="openTicketDetail"
                    @navigate-archived="router.push(`/station/boards/${board.shortKey}/archived`)"
                />
            </div>

            <BoardCreateTicketModal
                v-model="showCreateModal"
                :board-key="boardKey"
                :short-key="board.shortKey"
                :lane-options="createLaneOptions"
                :default-lane-id="defaultCreateLaneId"
                :members="members"
            />
        </template>
    </ViewContent>
</template>
