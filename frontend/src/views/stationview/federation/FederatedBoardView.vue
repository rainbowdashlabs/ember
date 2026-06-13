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
import IconButton from '@/components/button/IconButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import Modal from '@/components/feedback/Modal.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SearchInput from '@/components/input/text/SearchInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import MarkdownEditor from '@/components/input/MarkdownEditor.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import FederatedBoardAccessOverride from '@/views/stationview/federation/FederatedBoardAccessOverride.vue'
import TicketTile from '@/views/stationview/boards/boardview/TicketTile.vue'
import type { BoardLane, BoardTicket, BoardLabel } from '@/api/boards'
import { TicketPriority } from '@/api/boards'
import type { TicketPriorityName } from '@/api/boards'
import {useSession} from '@/composables/useSession'
import {
    type FederatedBoardDetail,
    BoardShareMode,
    getBoard as fedGetBoard,
    getLanes as fedGetLanes,
    listTickets as fedListTickets,
    getLabels as fedGetLabels,
    getAllTicketLabels as fedGetAllTicketLabels,
    searchTickets as fedSearchTickets,
    createTicket as fedCreateTicket,
    moveTicket as fedMoveTicket,
    reorderTickets as fedReorderTickets,
} from '@/api/federatedBoards'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const {canManageBoards} = useSession()

const partnerUid = computed(() => route.params.partnerUid as string)
const boardKey = computed(() => route.params.boardKey as string)

const boardDetail = ref<FederatedBoardDetail | null>(null)
const lanes = ref<BoardLane[]>([])
const tickets = ref<BoardTicket[]>([])
const allLabels = ref<BoardLabel[]>([])
const ticketLabelMap = ref<Map<number, number[]>>(new Map())
const loading = ref(true)
const error = ref('')

const isReadOnly = computed(() => boardDetail.value?.shareMode === BoardShareMode.READ_ONLY)
const isFull = computed(() => boardDetail.value?.shareMode === BoardShareMode.FULL)

const showCreateModal = ref(false)
const createTitle = ref('')
const createDescription = ref('')
const createLaneId = ref('')
const createPriority = ref<TicketPriorityName>(TicketPriority.MEDIUM)
const createError = ref('')

const showOverrideModal = ref(false)
const searchQuery = ref('')
const searchResults = ref<BoardTicket[] | null>(null)
const searching = ref(false)

async function loadData() {
    loading.value = true
    error.value = ''
    try {
        const [bd, l, tix, lb, tlm] = await Promise.all([
            fedGetBoard(partnerUid.value, boardKey.value),
            fedGetLanes(partnerUid.value, boardKey.value),
            fedListTickets(partnerUid.value, boardKey.value),
            fedGetLabels(partnerUid.value, boardKey.value),
            fedGetAllTicketLabels(partnerUid.value, boardKey.value),
        ])
        boardDetail.value = bd
        lanes.value = l
        tickets.value = tix
        allLabels.value = lb
        const map = new Map<number, number[]>()
        for (const { ticketId, labelId } of tlm) { if (!map.has(ticketId)) map.set(ticketId, []); map.get(ticketId)!.push(labelId) }
        ticketLabelMap.value = map
        if (!createLaneId.value) {
            const firstAllowed = bd.board.backlogLaneId ?? l.find(la => la.id !== bd.board.backlogLaneId)?.id
            if (firstAllowed) createLaneId.value = String(firstAllowed)
        }
    } catch {
        error.value = t('common.error')
    } finally {
        loading.value = false
    }
}

const board = computed(() => boardDetail.value?.board ?? null)

const visibleLanes = computed(() => lanes.value.filter(l => !board.value?.backlogLaneId || l.id !== board.value.backlogLaneId))

function ticketsForLane(laneId: number): BoardTicket[] {
    return tickets.value.filter(t => t.laneId === laneId).sort((a, b) => a.position - b.position)
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

function labelsForTicket(ticketId: number): BoardLabel[] {
    const ids = ticketLabelMap.value.get(ticketId) ?? []
    return allLabels.value.filter(l => ids.includes(l.id))
}

const createLaneOptions = computed(() => {
    const options: BoardLane[] = []
    const backlogLane = board.value?.backlogLaneId ? lanes.value.find(l => l.id === board.value!.backlogLaneId) : null
    if (backlogLane) options.push(backlogLane)
    const firstVisible = visibleLanes.value[0]
    if (firstVisible) options.push(firstVisible)
    return options
})

function laneName(laneId: number): string {
    return lanes.value.find(l => l.id === laneId)?.name ?? ''
}

function priorityIcon(priority: TicketPriorityName): string[] {
    switch (priority) {
        case TicketPriority.HIGHEST: return ['fas', 'angles-up']
        case TicketPriority.HIGH: return ['fas', 'angle-up']
        case TicketPriority.MEDIUM: return ['fas', 'equals']
        case TicketPriority.LOW: return ['fas', 'angle-down']
        case TicketPriority.LOWEST: return ['fas', 'angles-down']
        default: return ['fas', 'minus']
    }
}

function priorityColor(priority: TicketPriorityName): string {
    switch (priority) {
        case TicketPriority.HIGHEST: return 'text-red-500'
        case TicketPriority.HIGH: return 'text-orange-500'
        case TicketPriority.MEDIUM: return 'text-yellow-500'
        case TicketPriority.LOW: return 'text-blue-400'
        case TicketPriority.LOWEST: return 'text-gray-400'
        default: return 'text-gray-400'
    }
}

// -- Search --
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
            searchResults.value = await fedSearchTickets(partnerUid.value, boardKey.value, searchQuery.value.trim())
        } catch { /* ignore */ }
        finally { searching.value = false }
    }, 300)
}

function openTicketDetail(ticket: BoardTicket) {
    router.push(`/station/federation/boards/${partnerUid.value}/${boardKey.value}/tickets/${ticket.ticketNumber}`)
}

// -- Create ticket (FULL mode only) --
async function handleCreateTicket() {
    createError.value = ''
    if (!createTitle.value.trim()) {
        createError.value = t('common.requiredField')
        return
    }
    try {
        const created = await fedCreateTicket(partnerUid.value, boardKey.value, {
            laneId: Number(createLaneId.value),
            title: createTitle.value.trim(),
            description: createDescription.value.trim() || undefined,
            priority: createPriority.value,
        })
        showCreateModal.value = false
        createTitle.value = ''
        createDescription.value = ''
        createPriority.value = TicketPriority.MEDIUM
        router.push(`/station/federation/boards/${partnerUid.value}/${boardKey.value}/tickets/${created.ticketNumber}`)
    } catch {
        createError.value = t('common.error')
    }
}

// -- Drag and drop (FULL mode only) --
const dragTicket = ref<BoardTicket | null>(null)
const dropLaneId = ref<number | null>(null)
const dropPosition = ref<number | null>(null)

function onTicketDragStart(ticket: BoardTicket, event: DragEvent) {
    if (isReadOnly.value) return
    dragTicket.value = ticket
    if (event.dataTransfer) {
        event.dataTransfer.effectAllowed = 'move'
        event.dataTransfer.setData('text/plain', String(ticket.id))
    }
}

function onLaneDragOver(laneId: number, event: DragEvent) {
    if (isReadOnly.value) return
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
    if (!dragTicket.value || isReadOnly.value) return
    const ticket = dragTicket.value
    const pos = dropPosition.value ?? 0
    dragTicket.value = null
    dropLaneId.value = null
    dropPosition.value = null

    const otherTickets = tickets.value.filter(t => t.laneId === laneId && t.id !== ticket.id).sort((a, b) => a.position - b.position)
    otherTickets.splice(pos, 0, ticket)
    const updatedTicket = { ...ticket, laneId, laneEnteredAt: ticket.laneId !== laneId ? new Date().toISOString() : ticket.laneEnteredAt }
    tickets.value = tickets.value.filter(t => t.id !== ticket.id).map(t => {
        const idx = otherTickets.findIndex(ot => ot.id === t.id)
        return idx >= 0 ? { ...t, position: idx } : t
    })
    tickets.value.push({ ...updatedTicket, position: pos })

    if (ticket.laneId === laneId) {
        try {
            await fedReorderTickets(partnerUid.value, boardKey.value, ticket.ticketNumber, { laneId, orderedIds: otherTickets.map(t => t.id) })
        } catch { await loadData() }
    } else {
        try {
            await fedMoveTicket(partnerUid.value, boardKey.value, ticket.ticketNumber, { toLaneId: laneId, position: pos })
        } catch { await loadData() }
    }
}

function onDragEnd() {
    dragTicket.value = null
    dropLaneId.value = null
    dropPosition.value = null
}

onMounted(loadData)
watch([partnerUid, boardKey], loadData)
</script>

<template>
    <ViewContent>
        <Spinner v-if="loading" />
        <Alert v-else-if="error" variant="error">{{ error }}</Alert>
        <template v-else-if="board">
            <!-- Header -->
            <div class="flex items-center justify-between mb-4 flex-wrap gap-2">
                <div class="flex items-center gap-3">
                    <IconButton :icon="['fas', 'arrow-left']" :label="t('common.back')" @click="router.push('/station/federation/boards')" />
                    <SectionHeader>{{ board.name }}</SectionHeader>
                    <span class="text-xs font-mono text-(--text-muted) bg-(--bg-accent) px-1.5 py-0.5 rounded">{{ board.shortKey }}</span>
                    <!-- Share mode badge -->
                    <InfoBadge v-if="isReadOnly" class="inline-flex items-center gap-1">
                        <font-awesome-icon :icon="['fas', 'eye']" class="text-[0.65rem]" />
                        {{ t('boards.readOnlyBadge') }}
                    </InfoBadge>
                    <SuccessBadge v-else class="inline-flex items-center gap-1">
                        <font-awesome-icon :icon="['fas', 'pen']" class="text-[0.65rem]" />
                        {{ t('boards.fullAccessBadge') }}
                    </SuccessBadge>
                </div>
                <div class="flex items-center gap-2">
                    <!-- Search (both modes) -->
                    <div class="relative">
                        <SearchInput v-model="searchQuery" :placeholder="t('boards.searchTickets')" class="w-96" @input="onSearchInput" />
                        <div v-if="searchResults && searchResults.length > 0" class="absolute z-20 mt-1 w-[28rem] right-0 rounded-theme border border-(--border) bg-(--bg) shadow-lg overflow-hidden">
                            <div
                                v-for="result in searchResults"
                                :key="result.id"
                                class="px-3 py-2 text-sm cursor-pointer hover:bg-primary/5 flex items-center gap-2"
                                @click="openTicketDetail(result); searchQuery = ''; searchResults = null"
                            >
                                <div class="flex-1 min-w-0">
                                    <div class="flex items-center gap-1.5">
                                        <span class="font-mono text-xs text-(--text-muted) shrink-0">{{ board.shortKey }}-{{ result.ticketNumber }}</span>
                                        <span class="truncate">{{ result.title }}</span>
                                    </div>
                                </div>
                                <div class="flex items-center gap-2 shrink-0 text-xs text-(--text-muted)">
                                    <span class="px-1.5 py-0.5 rounded bg-(--bg-accent) text-[0.65rem]">{{ laneName(result.laneId) }}</span>
                                    <font-awesome-icon :icon="priorityIcon(result.priority)" :class="priorityColor(result.priority)" />
                                </div>
                            </div>
                        </div>
                    </div>
                    <SecondaryButton v-if="canManageBoards()" @click="showOverrideModal = true">
                        <font-awesome-icon :icon="['fas', 'shield']" class="mr-1" />
                        {{ t('boards.accessOverride') }}
                    </SecondaryButton>
                    <!-- Create button (FULL mode only) -->
                    <PrimaryButton v-if="isFull" @click="showCreateModal = true">
                        <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />
                        {{ t('boards.createTicket') }}
                    </PrimaryButton>
                </div>
            </div>

            <!-- Federation info line -->
            <div class="flex items-center gap-2 mb-4 text-sm text-(--text-muted)">
                <font-awesome-icon :icon="['fas', 'share-nodes']" />
                <span>{{ t('boards.federatedFrom') }}: <strong class="text-(--text)">{{ boardDetail?.stationName }}</strong></span>
            </div>

            <!-- Kanban board -->
            <div class="flex flex-col md:flex-row gap-4 md:overflow-x-auto pb-4" style="min-height: 200px">
                <div
                    v-for="lane in visibleLanes"
                    :key="lane.id"
                    class="md:flex-1 md:min-w-[14rem] md:max-w-[24rem] bg-bg-light-accent dark:bg-bg-dark-accent border border-[var(--border)] rounded-lg p-3 border-t-2 transition-colors"
                    :style="{ borderTopColor: lane.color ?? 'var(--primary)' }"
                    :class="{ 'bg-primary/5': dropLaneId === lane.id && dragTicket }"
                    @dragover="isFull ? onLaneDragOver(lane.id, $event) : undefined"
                    @dragleave="isFull ? onLaneDragLeave($event) : undefined"
                    @drop="isFull ? onLaneDrop(lane.id) : undefined"
                >
                    <!-- Lane header -->
                    <div class="flex items-center justify-between mb-3">
                        <SubHeader class="text-sm text-[var(--text-muted)] uppercase tracking-wide">{{ lane.name }}</SubHeader>
                        <BaseBadge bg-class="bg-[var(--bg)]" class="text-[var(--text-muted)]">{{ visibleTicketsForLane(lane.id).length }}</BaseBadge>
                    </div>

                    <!-- Tickets -->
                    <div class="min-h-[3rem]">
                        <template v-for="(ticket, idx) in visibleTicketsForLane(lane.id)" :key="ticket.id">
                            <!-- Drop indicator before this ticket -->
                            <div v-if="isFull && dropLaneId === lane.id && dropPosition === idx && dragTicket && dragTicket.id !== ticket.id"
                                class="h-12 mb-2 rounded-lg border-2 border-dashed border-primary/40 bg-primary/5" />
                            <div
                                :data-ticket-id="ticket.id"
                                :draggable="isFull"
                                class="mb-2"
                                :class="{ 'opacity-30': dragTicket?.id === ticket.id }"
                                @dragstart="isFull ? onTicketDragStart(ticket, $event) : undefined"
                                @dragend="isFull ? onDragEnd() : undefined"
                            >
                                <TicketTile
                                    :ticket="ticket"
                                    :short-key="board.shortKey"
                                    :labels="labelsForTicket(ticket.id)"
                                    :attachment-count="ticket.attachmentCount"
                                    @click="openTicketDetail"
                                />
                            </div>
                        </template>
                        <!-- Drop indicator at end of lane -->
                        <div v-if="isFull && dropLaneId === lane.id && dragTicket && dropPosition !== null && dropPosition >= visibleTicketsForLane(lane.id).filter(t => t.id !== dragTicket!.id).length"
                            class="h-12 rounded-lg border-2 border-dashed border-primary/40 bg-primary/5" />
                    </div>

                    <!-- Archived count -->
                    <div v-if="isLastLane(lane.id) && archivedCountForLane(lane.id) > 0" class="mt-2 text-xs text-(--text-muted) text-center py-2">
                        {{ archivedCountForLane(lane.id) }} {{ t('boards.archived') }}
                    </div>

                    <!-- Empty state -->
                    <p v-if="visibleTicketsForLane(lane.id).length === 0 && !dragTicket" class="text-xs text-[var(--text-muted)] text-center py-4">
                        {{ t('boards.noTickets') }}
                    </p>
                </div>
            </div>

            <!-- Create ticket modal (FULL mode only) -->
            <Modal v-if="isFull" v-model="showCreateModal">
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
                    <Alert v-if="createError" variant="error">{{ createError }}</Alert>
                    <div class="flex justify-end">
                        <PrimaryButton @click="handleCreateTicket">{{ t('common.create') }}</PrimaryButton>
                    </div>
                </div>
            </Modal>
        </template>

        <FederatedBoardAccessOverride
            v-if="showOverrideModal"
            v-model="showOverrideModal"
            :partner-uid="partnerUid"
            :board-key="boardKey"
        />
    </ViewContent>
</template>
