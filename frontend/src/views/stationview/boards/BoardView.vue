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
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import { contrastTextColor } from '@/theme/contrast'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import Modal from '@/components/feedback/Modal.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SearchInput from '@/components/input/text/SearchInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import MultiSelectDropdown from '@/components/input/select/MultiSelectDropdown.vue'
import MemberSelectInput from '@/components/input/select/MemberSelectInput.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import MarkdownEditor from '@/components/input/MarkdownEditor.vue'
import KanbanLane from './boardview/KanbanLane.vue'
import UserAvatar from '@/components/avatar/UserAvatar.vue'
import { boards, stationMembers } from '@/api'
import type { MemberCompletion } from '@/api/stationMembers'
import type { Board, BoardLane, BoardTicket, BoardLabel, TicketPriorityName } from '@/api/boards'
import { TicketPriority } from '@/api/boards'
import { priorityIcon, priorityColor } from '@/util/ticketPriority'
import { useSession } from '@/composables/useSession'
import { useAsyncLoader } from '@/composables/useAsyncLoader'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const { canManageBoards, sessionInfo } = useSession()

const boardKey = computed(() => route.params.boardKey as string)
const board = ref<Board | null>(null)
const lanes = ref<BoardLane[]>([])
const tickets = ref<BoardTicket[]>([])

const assigneeFilter = ref<Set<string>>(new Set())
const labelFilter = ref<string[]>([])
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
    if (!createLaneId.value) {
        const firstAllowed = b.backlogLaneId ?? l.find(la => la.id !== b.backlogLaneId)?.id
        if (firstAllowed) createLaneId.value = String(firstAllowed)
    }
})

const visibleLanes = computed(() => lanes.value.filter(l => !board.value?.backlogLaneId || l.id !== board.value.backlogLaneId))
const backlogLane = computed(() => board.value?.backlogLaneId ? lanes.value.find(l => l.id === board.value!.backlogLaneId) ?? null : null)

function ticketsForLane(laneId: number): BoardTicket[] {
    let filtered = tickets.value.filter(t => t.laneId === laneId)
    if (assigneeFilter.value.size > 0) {
        filtered = filtered.filter(t => t.assignee !== null && assigneeFilter.value.has(t.assignee.memberUid))
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

const labelFilterOptions = computed(() => allLabels.value.map(l => ({ value: String(l.id), label: l.name })))

const createLaneOptions = computed(() => {
    const options: BoardLane[] = []
    if (backlogLane.value) options.push(backlogLane.value)
    const firstVisible = visibleLanes.value[0]
    if (firstVisible) options.push(firstVisible)
    return options
})

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
    const uids = new Set(tickets.value.map(t => t.assignee?.memberUid).filter(Boolean) as string[])
    const list = members.value.filter(m => uids.has(m.memberUid))
    const i = list.findIndex(m => m.memberUid === sessionInfo.value?.member?.uid)
    return i <= 0 ? list : [list[i], ...list.slice(0, i), ...list.slice(i + 1)]
})
async function handleCreateTicket() {
    createError.value = ''
    if (!createTitle.value.trim()) {
        createError.value = t('common.requiredField')
        return
    }
    try {
        const created = await boards.createTicket(boardKey.value, {
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
        router.push(`/station/boards/${boardKey.value}/tickets/${created.ticketNumber}`)
    } catch {
        createError.value = t('common.error')
    }
}

function toggleAssigneeFilter(memberUid: string) {
    const next = new Set(assigneeFilter.value)
    if (next.has(memberUid)) next.delete(memberUid); else next.add(memberUid)
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
            searchResults.value = await boards.searchTickets(boardKey.value, searchQuery.value.trim())
        } catch { /* ignore */ }
        finally { searching.value = false }
    }, 300)
}

function openTicketDetail(ticket: BoardTicket) {
    router.push(`/station/boards/${boardKey.value}/tickets/${ticket.ticketNumber}`)
}

function laneName(laneId: number): string {
    return lanes.value.find(l => l.id === laneId)?.name ?? ''
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

    // Optimistic update: apply changes locally first to avoid flicker
    const otherTickets = tickets.value.filter(t => t.laneId === laneId && t.id !== ticket.id).sort((a, b) => a.position - b.position)
    otherTickets.splice(pos, 0, ticket)
    // Update positions and lane assignment in local state
    const updatedTicket = { ...ticket, laneId, laneEnteredAt: ticket.laneId !== laneId ? new Date().toISOString() : ticket.laneEnteredAt }
    tickets.value = tickets.value.filter(t => t.id !== ticket.id).map(t => {
        const idx = otherTickets.findIndex(ot => ot.id === t.id)
        return idx >= 0 ? { ...t, position: idx } : t
    })
    tickets.value.push({ ...updatedTicket, position: pos })

    if (ticket.laneId === laneId) {
        try {
            await boards.reorderTickets(boardKey.value, ticket.ticketNumber, { laneId, orderedIds: otherTickets.map(t => t.id) })
        } catch { await reload() }
    } else {
        try {
            await boards.moveTicket(boardKey.value, ticket.ticketNumber, { toLaneId: laneId, position: pos })
        } catch { await reload() }
    }
}

function onDragEnd() {
    dragTicket.value = null
    dropLaneId.value = null
    dropPosition.value = null
}

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
            <!-- Header -->
            <div class="flex items-center justify-between mb-4 flex-wrap gap-2">
                <div class="flex items-center gap-3">
                    <span class="text-xs font-mono text-(--text-muted) bg-(--bg-accent) px-1.5 py-0.5 rounded">{{ board.shortKey }}</span>
                </div>
                <div class="flex items-center gap-2">
                    <div class="relative">
                        <SearchInput v-model="searchQuery" :placeholder="t('boards.searchTickets')" class="w-96" @input="onSearchInput" />
                        <div v-if="searchResults && searchResults.length > 0" class="absolute z-20 mt-1 w-[28rem] right-0 rounded-theme border border-(--border) bg-(--bg) shadow-lg overflow-hidden">
                            <div
                                v-for="result in searchResults"
                                :key="result.id"
                                class="px-3 py-2 text-sm cursor-pointer hover:bg-primary/5 flex items-center gap-2"
                                @click="openTicketDetail(result); searchQuery = ''; searchResults = null"
                            >
                                <!-- Left: id + title + labels -->
                                <div class="flex-1 min-w-0">
                                    <div class="flex items-center gap-1.5">
                                        <span class="font-mono text-xs text-(--text-muted) shrink-0">{{ board.shortKey }}-{{ result.ticketNumber }}</span>
                                        <span class="truncate">{{ result.title }}</span>
                                    </div>
                                    <div v-if="labelsForTicket(result.id).length > 0" class="flex flex-wrap gap-1 mt-0.5">
                                        <BaseBadge v-for="label in labelsForTicket(result.id)" :key="label.id" bg-class="" :style="{ backgroundColor: label.color, color: contrastTextColor(label.color) }">{{ label.name }}</BaseBadge>
                                    </div>
                                </div>
                                <!-- Right: lane, priority, avatar -->
                                <div class="flex items-center gap-2 shrink-0 text-xs text-(--text-muted)">
                                    <span class="px-1.5 py-0.5 rounded bg-(--bg-accent) text-[0.65rem]">{{ laneName(result.laneId) }}</span>
                                    <font-awesome-icon :icon="priorityIcon(result.priority)" :class="priorityColor(result.priority)" />
                                    <UserAvatar v-if="result.assignee" :identity="result.assignee" size="sm" />
                                </div>
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
                        @click="router.push(`/station/boards/${board.shortKey}/settings`)"
                    />
                </div>
            </div>

            <!-- Backlog + Assignee filter bar -->
            <div class="flex items-center mb-4 gap-3">
                <IconButton v-if="backlogLane" :icon="['fas', 'inbox']" :label="t('boards.showBacklog')" class="text-(--text-muted)" @click="router.push(`/station/boards/${board.shortKey}/backlog`)" />
                <div v-if="assignees.length > 0" class="flex items-center">
                    <div class="cursor-pointer rounded-full transition-all" :class="assigneeFilter.size === 0 ? 'ring-2 ring-primary' : 'opacity-60 hover:opacity-100'" :title="t('boards.allMembers')" @click="assigneeFilter = new Set()">
                        <div class="h-8 w-8 rounded-full bg-primary/15 text-primary font-bold flex items-center justify-center text-xs">
                            <font-awesome-icon :icon="['fas', 'users']" />
                        </div>
                    </div>
                    <div v-for="member in assignees" :key="member.id" class="cursor-pointer rounded-full transition-all -ml-1.5" :class="assigneeFilter.has(member.memberUid) ? 'ring-2 ring-primary z-10' : 'opacity-70 hover:opacity-100'" :title="member.name" @click="toggleAssigneeFilter(member.memberUid)">
                        <UserAvatar :identity="member" :name="member.name" size="md" />
                    </div>
                </div>
                <!-- Label filter -->
                <MultiSelectDropdown
                    v-if="allLabels.length > 0"
                    v-model="labelFilter"
                    :options="labelFilterOptions"
                    :placeholder="t('boards.labels')"
                />
            </div>

            <!-- Kanban board -->
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
                            <SelectInput v-model="createLaneId" class="w-full">
                                <option v-for="lane in createLaneOptions" :key="lane.id" :value="lane.id">{{ lane.name }}</option>
                            </SelectInput>
                        </div>
                        <div>
                            <FieldLabel class="mb-1">{{ t('boards.priority') }}</FieldLabel>
                            <SelectInput v-model="createPriority" class="w-full">
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
                    <div class="flex items-center justify-between">
                        <SecondaryButton @click="showCreateModal = false; router.push({ path: `/station/boards/${board!.shortKey}/tickets/new`, query: { title: createTitle || undefined, description: createDescription || undefined, laneId: createLaneId || undefined, priority: createPriority !== TicketPriority.MEDIUM ? createPriority : undefined, assignee: createAssignee || undefined, dueDate: createDueDate || undefined } })">
                            <font-awesome-icon :icon="['fas', 'expand']" class="mr-1" />
                            {{ t('boards.moreOptions') }}
                        </SecondaryButton>
                        <PrimaryButton @click="handleCreateTicket">{{ t('common.create') }}</PrimaryButton>
                    </div>
                </div>
            </Modal>
        </template>
    </ViewContent>
</template>
