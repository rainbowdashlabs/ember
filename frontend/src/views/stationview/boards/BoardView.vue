/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import Modal from '@/components/feedback/Modal.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import MemberSelectInput from '@/components/input/select/MemberSelectInput.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import MarkdownEditor from '@/components/input/MarkdownEditor.vue'
import TicketTile from './boardview/TicketTile.vue'
import UserAvatar from '@/components/avatar/UserAvatar.vue'
import { boards, stationMembers } from '@/api'
import type { Board, BoardLane, BoardTicket, BoardLabel } from '@/api/boards'
import { TicketPriority } from '@/api/boards'
import type { TicketPriorityName } from '@/api/boards'
import { useSession } from '@/composables/useSession'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const { canManageBoards } = useSession()

const boardId = computed(() => Number(route.params.boardId))
const board = ref<Board | null>(null)
const lanes = ref<BoardLane[]>([])
const tickets = ref<BoardTicket[]>([])
const loading = ref(true)
const error = ref('')

const assigneeFilter = ref<Set<number>>(new Set())
const labelFilter = ref<Set<number>>(new Set())
const allLabels = ref<BoardLabel[]>([])
const ticketLabelMap = ref<Map<number, number[]>>(new Map())
const showCreateModal = ref(false)
const createTitle = ref('')
const createDescription = ref('')
const createLaneId = ref('')
const createPriority = ref<TicketPriorityName>(TicketPriority.MEDIUM)
const createAssignee = ref('')
const createDueDate = ref('')
const createError = ref('')


const searchQuery = ref('')
const searchResults = ref<BoardTicket[] | null>(null)
const searching = ref(false)

const members = ref<{ id: number; name: string }[]>([])

async function loadData() {
    loading.value = true
    error.value = ''
    try {
        const [b, l, t, m, lb, tlm] = await Promise.all([
            boards.getBoard(boardId.value),
            boards.getLanes(boardId.value),
            boards.listTickets(boardId.value),
            stationMembers.listCompletions(),
            boards.getLabels(boardId.value),
            boards.getAllTicketLabels(boardId.value),
        ])
        board.value = b
        lanes.value = l
        tickets.value = t
        members.value = m
        allLabels.value = lb
        const map = new Map<number, number[]>()
        for (const { ticketId, labelId } of tlm) { if (!map.has(ticketId)) map.set(ticketId, []); map.get(ticketId)!.push(labelId) }
        ticketLabelMap.value = map
        if (l.length > 0 && !createLaneId.value) {
            createLaneId.value = String(l[0].id)
        }
    } catch {
        error.value = t('common.error')
    } finally {
        loading.value = false
    }
}

const visibleLanes = computed(() => lanes.value.filter(l => !board.value?.backlogLaneId || l.id !== board.value.backlogLaneId))
const backlogLane = computed(() => board.value?.backlogLaneId ? lanes.value.find(l => l.id === board.value!.backlogLaneId) ?? null : null)

function ticketsForLane(laneId: number): BoardTicket[] {
    let filtered = tickets.value.filter(t => t.laneId === laneId)
    if (assigneeFilter.value.size > 0) {
        filtered = filtered.filter(t => t.assignedMemberId !== null && assigneeFilter.value.has(t.assignedMemberId))
    }
    if (labelFilter.value.size > 0) {
        filtered = filtered.filter(t => { const ids = ticketLabelMap.value.get(t.id) ?? []; return ids.some(id => labelFilter.value.has(id)) })
    }
    return filtered.sort((a, b) => a.position - b.position)
}

function labelsForTicket(ticketId: number): BoardLabel[] {
    const ids = ticketLabelMap.value.get(ticketId) ?? []
    return allLabels.value.filter(l => ids.includes(l.id))
}

function toggleLabelFilter(labelId: number) {
    const next = new Set(labelFilter.value)
    if (next.has(labelId)) next.delete(labelId); else next.add(labelId)
    labelFilter.value = next
}

function isLastLane(laneId: number): boolean {
    const vl = visibleLanes.value
    return vl.length > 0 && vl[vl.length - 1].id === laneId
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
    const ids = new Set(tickets.value.map(t => t.assignedMemberId).filter(Boolean) as number[])
    return members.value.filter(m => ids.has(m.id))
})

async function handleCreateTicket() {
    createError.value = ''
    if (!createTitle.value.trim()) {
        createError.value = t('common.requiredField')
        return
    }
    try {
        await boards.createTicket(boardId.value, {
            laneId: Number(createLaneId.value),
            title: createTitle.value.trim(),
            description: createDescription.value.trim() || undefined,
            priority: createPriority.value,
            assignedMemberId: createAssignee.value ? Number(createAssignee.value) : undefined,
            dueDate: createDueDate.value || undefined,
        })
        showCreateModal.value = false
        createTitle.value = ''
        createDescription.value = ''
        createPriority.value = TicketPriority.MEDIUM
        createAssignee.value = ''
        createDueDate.value = ''
        await loadData()
    } catch {
        createError.value = t('common.error')
    }
}

function toggleAssigneeFilter(memberId: number) {
    const next = new Set(assigneeFilter.value)
    if (next.has(memberId)) {
        next.delete(memberId)
    } else {
        next.add(memberId)
    }
    assigneeFilter.value = next
}

let searchTimeout: ReturnType<typeof setTimeout> | null = null
function onSearchInput() {
    if (searchTimeout) clearTimeout(searchTimeout)
    if (!searchQuery.value.trim()) {
        searchResults.value = null
        return
    }
    searchTimeout = setTimeout(async () => {
        searching.value = true
        try {
            searchResults.value = await boards.searchTickets(boardId.value, searchQuery.value.trim())
        } catch { /* ignore */ }
        finally { searching.value = false }
    }, 300)
}

function openTicketDetail(ticket: BoardTicket) {
    router.push(`/station/boards/${boardId.value}/tickets/${ticket.id}`)
}

// -- Drag and drop --
const dragTicket = ref<BoardTicket | null>(null)
const dropLaneId = ref<number | null>(null)
const dropPosition = ref<number | null>(null)

function onTicketDragStart(ticket: BoardTicket, event: DragEvent) {
    dragTicket.value = ticket
    if (event.dataTransfer) {
        event.dataTransfer.effectAllowed = 'move'
        event.dataTransfer.setData('text/plain', String(ticket.id))
    }
}

function onLaneDragOver(laneId: number, event: DragEvent) {
    event.preventDefault()
    dropLaneId.value = laneId
    const container = event.currentTarget as HTMLElement
    const ticketElements = container.querySelectorAll('[data-ticket-id]')
    let pos = 0
    for (const el of ticketElements) {
        if (dragTicket.value && el.getAttribute('data-ticket-id') === String(dragTicket.value.id)) continue
        const rect = el.getBoundingClientRect()
        if (event.clientY > rect.top + rect.height / 2) pos++
    }
    dropPosition.value = pos
}

function onLaneDragLeave(event: DragEvent) {
    const target = event.currentTarget as HTMLElement
    if (!target.contains(event.relatedTarget as Node)) {
        dropLaneId.value = null
        dropPosition.value = null
    }
}

async function onLaneDrop(laneId: number) {
    if (!dragTicket.value) return
    const ticket = dragTicket.value
    const pos = dropPosition.value ?? 0
    dragTicket.value = null
    dropLaneId.value = null
    dropPosition.value = null

    if (ticket.laneId === laneId) {
        const laneTickets = ticketsForLane(laneId).filter(t => t.id !== ticket.id)
        laneTickets.splice(pos, 0, ticket)
        try {
            await boards.reorderTickets(boardId.value, ticket.id, { laneId, orderedIds: laneTickets.map(t => t.id) })
            await loadData()
        } catch { /* ignore */ }
    } else {
        try {
            await boards.moveTicket(boardId.value, ticket.id, { toLaneId: laneId, position: pos })
            await loadData()
        } catch { /* ignore */ }
    }
}

function onDragEnd() {
    dragTicket.value = null
    dropLaneId.value = null
    dropPosition.value = null
}

onMounted(loadData)
watch(boardId, loadData)
</script>

<template>
    <ViewContent>
        <Spinner v-if="loading" />
        <Alert v-else-if="error" variant="error">{{ error }}</Alert>
        <template v-else-if="board">
            <!-- Header -->
            <div class="flex items-center justify-between mb-4 flex-wrap gap-2">
                <div class="flex items-center gap-3">
                    <SectionHeader>{{ board.name }}</SectionHeader>
                    <span class="text-xs font-mono text-(--text-muted) bg-(--bg-accent) px-1.5 py-0.5 rounded">{{ board.shortKey }}</span>
                </div>
                <div class="flex items-center gap-2">
                    <div class="relative">
                        <TextInput v-model="searchQuery" :placeholder="t('boards.searchTickets')" class="w-64" @input="onSearchInput" />
                        <div v-if="searchResults && searchResults.length > 0" class="absolute z-20 mt-1 w-full rounded-theme border border-(--border) bg-(--bg) shadow-lg overflow-hidden max-h-48 overflow-y-auto">
                            <div
                                v-for="result in searchResults"
                                :key="result.id"
                                class="px-3 py-2 text-sm cursor-pointer hover:bg-primary/5 flex items-center gap-2"
                                @click="openTicketDetail(result); searchQuery = ''; searchResults = null"
                            >
                                <span class="font-mono text-(--text-muted)">{{ board.shortKey }}-{{ result.ticketNumber }}</span>
                                <span class="truncate">{{ result.title }}</span>
                            </div>
                        </div>
                    </div>
                    <PrimaryButton @click="showCreateModal = true">
                        <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />
                        {{ t('boards.createTicket') }}
                    </PrimaryButton>
                    <IconButton
                        v-if="canManageBoards()"
                        :icon="['fas', 'gears']"
                        :label="t('boards.settings')"
                        @click="router.push(`/station/boards/${board.id}/settings`)"
                    />
                </div>
            </div>

            <!-- Backlog + Assignee filter bar -->
            <div class="flex items-center mb-4 gap-3">
                <IconButton v-if="backlogLane" :icon="['fas', 'inbox']" :label="t('boards.showBacklog')" class="text-(--text-muted)" @click="router.push(`/station/boards/${board.id}/backlog`)" />
                <div v-if="assignees.length > 0" class="flex items-center">
                    <div class="cursor-pointer rounded-full transition-all" :class="assigneeFilter.size === 0 ? 'ring-2 ring-primary' : 'opacity-60 hover:opacity-100'" :title="t('boards.allMembers')" @click="assigneeFilter = new Set()">
                        <div class="h-8 w-8 rounded-full bg-primary/15 text-primary font-bold flex items-center justify-center text-xs">
                            <font-awesome-icon :icon="['fas', 'users']" />
                        </div>
                    </div>
                    <div v-for="member in assignees" :key="member.id" class="cursor-pointer rounded-full transition-all -ml-1.5" :class="assigneeFilter.has(member.id) ? 'ring-2 ring-primary z-10' : 'opacity-70 hover:opacity-100'" :title="member.name" @click="toggleAssigneeFilter(member.id)">
                        <UserAvatar :member-id="member.id" :name="member.name" size="md" />
                    </div>
                </div>
                <!-- Label filter -->
                <div v-if="allLabels.length > 0" class="flex flex-wrap gap-1 items-center">
                    <span v-for="label in allLabels" :key="label.id"
                        class="text-xs px-2 py-0.5 rounded-full cursor-pointer transition-all"
                        :class="labelFilter.has(label.id) ? 'ring-2 ring-offset-1 ring-[var(--text)]' : 'opacity-70 hover:opacity-100'"
                        :style="{ backgroundColor: label.color, color: 'white' }"
                        @click="toggleLabelFilter(label.id)">{{ label.name }}</span>
                    <span v-if="labelFilter.size > 0" class="text-xs text-(--text-muted) cursor-pointer hover:underline ml-1" @click="labelFilter = new Set()">
                        <font-awesome-icon :icon="['fas', 'xmark']" class="text-[0.6rem]" />
                    </span>
                </div>
            </div>

            <!-- Kanban board -->
            <div class="flex flex-col md:flex-row gap-4 md:overflow-x-auto pb-4" style="min-height: 200px">
                <div
                    v-for="lane in visibleLanes"
                    :key="lane.id"
                    class="md:flex-1 md:min-w-[14rem] md:max-w-[24rem] bg-bg-light-accent dark:bg-bg-dark-accent border border-[var(--border)] rounded-lg p-3 border-t-2 transition-colors"
                    :style="{ borderTopColor: lane.color ?? 'var(--primary)' }"
                    :class="{ 'bg-primary/5': dropLaneId === lane.id && dragTicket }"
                    @dragover="onLaneDragOver(lane.id, $event)"
                    @dragleave="onLaneDragLeave($event)"
                    @drop="onLaneDrop(lane.id)"
                >
                    <!-- Lane header -->
                    <div class="flex items-center justify-between mb-3">
                        <SubHeader class="text-sm text-[var(--text-muted)] uppercase tracking-wide">{{ lane.name }}</SubHeader>
                        <span class="text-xs text-[var(--text-muted)] bg-[var(--bg)] px-1.5 py-0.5 rounded-full">{{ visibleTicketsForLane(lane.id).length }}</span>
                    </div>

                    <!-- Tickets -->
                    <div class="min-h-[3rem]">
                        <template v-for="(ticket, idx) in visibleTicketsForLane(lane.id)" :key="ticket.id">
                            <!-- Drop indicator before this ticket -->
                            <div v-if="dropLaneId === lane.id && dropPosition === idx && dragTicket && dragTicket.id !== ticket.id"
                                class="h-12 mb-2 rounded-lg border-2 border-dashed border-primary/40 bg-primary/5" />
                            <div
                                :data-ticket-id="ticket.id"
                                draggable="true"
                                class="mb-2"
                                :class="{ 'opacity-30': dragTicket?.id === ticket.id }"
                                @dragstart="onTicketDragStart(ticket, $event)"
                                @dragend="onDragEnd"
                            >
                                <TicketTile
                                    :ticket="ticket"
                                    :short-key="board.shortKey"
                                    :member-name="ticket.assignedMemberId ? members.find(m => m.id === ticket.assignedMemberId)?.name : undefined"
                                    :labels="labelsForTicket(ticket.id)"
                                    :attachment-count="ticket.attachmentCount"
                                    @click="openTicketDetail"
                                />
                            </div>
                        </template>
                        <!-- Drop indicator at end of lane -->
                        <div v-if="dropLaneId === lane.id && dragTicket && dropPosition !== null && dropPosition >= visibleTicketsForLane(lane.id).filter(t => t.id !== dragTicket!.id).length"
                            class="h-12 rounded-lg border-2 border-dashed border-primary/40 bg-primary/5" />
                    </div>

                    <!-- Archived count -->
                    <div v-if="isLastLane(lane.id) && archivedCountForLane(lane.id) > 0" class="mt-2">
                        <SecondaryButton class="w-full text-xs" @click="router.push(`/station/boards/${board.id}/archived`)">
                            {{ archivedCountForLane(lane.id) }} {{ t('boards.archived') }}
                        </SecondaryButton>
                    </div>

                    <!-- Empty state (only when not dragging) -->
                    <p v-if="visibleTicketsForLane(lane.id).length === 0 && !dragTicket" class="text-xs text-[var(--text-muted)] text-center py-4">
                        {{ t('boards.noTickets') }}
                    </p>
                </div>
            </div>

            <!-- Create ticket modal -->
            <Modal v-model="showCreateModal">
                <SubHeader class="mb-4">{{ t('boards.createTicket') }}</SubHeader>
                <div class="space-y-4">
                    <div>
                        <FieldLabel class="mb-1">{{ t('boards.ticketTitle') }} *</FieldLabel>
                        <TextInput v-model="createTitle" />
                    </div>
                    <div>
                        <FieldLabel class="mb-1">{{ t('boards.ticketDescription') }}</FieldLabel>
                        <MarkdownEditor v-model="createDescription" :placeholder="t('boards.ticketDescription')" />
                    </div>
                    <div class="grid grid-cols-2 gap-4">
                        <div>
                            <FieldLabel class="mb-1">{{ t('boards.lanes') }}</FieldLabel>
                            <SelectInput v-model="createLaneId">
                                <option v-for="lane in lanes" :key="lane.id" :value="lane.id">{{ lane.name }}</option>
                            </SelectInput>
                        </div>
                        <div>
                            <FieldLabel class="mb-1">{{ t('boards.priority') }}</FieldLabel>
                            <SelectInput v-model="createPriority">
                                <option :value="TicketPriority.LOWEST">{{ t('boards.priorityLowest') }}</option>
                                <option :value="TicketPriority.LOW">{{ t('boards.priorityLow') }}</option>
                                <option :value="TicketPriority.MEDIUM">{{ t('boards.priorityMedium') }}</option>
                                <option :value="TicketPriority.HIGH">{{ t('boards.priorityHigh') }}</option>
                                <option :value="TicketPriority.HIGHEST">{{ t('boards.priorityHighest') }}</option>
                            </SelectInput>
                        </div>
                    </div>
                    <div class="grid grid-cols-2 gap-4">
                        <div>
                            <FieldLabel class="mb-1">{{ t('boards.assignee') }}</FieldLabel>
                            <MemberSelectInput v-model="createAssignee" :members="members" :placeholder="t('boards.unassigned')" />
                        </div>
                        <div>
                            <FieldLabel class="mb-1">{{ t('boards.dueDate') }}</FieldLabel>
                            <DateInput v-model="createDueDate" />
                        </div>
                    </div>
                    <Alert v-if="createError" variant="error">{{ createError }}</Alert>
                    <div class="flex justify-end">
                        <PrimaryButton @click="handleCreateTicket">{{ t('common.create') }}</PrimaryButton>
                    </div>
                </div>
            </Modal>

        </template>
    </ViewContent>
</template>
