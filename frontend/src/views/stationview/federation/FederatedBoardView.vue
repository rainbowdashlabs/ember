/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import FederatedBoardAccessOverride from '@/views/stationview/federation/FederatedBoardAccessOverride.vue'
import FederatedBoardHeader from '@/views/stationview/federation/federatedboardview/FederatedBoardHeader.vue'
import FederatedBoardLane from '@/views/stationview/federation/federatedboardview/FederatedBoardLane.vue'
import FederatedBoardCreateTicketModal
  from '@/views/stationview/federation/federatedboardview/FederatedBoardCreateTicketModal.vue'
import type {BoardLane, BoardTicket, BoardLabel} from '@/api/boards'
import {TicketPriority} from '@/api/boards'
import type {TicketPriorityName} from '@/api/boards'
import {priorityIcon, priorityColor} from '@/util/ticketPriority'
import {useSession} from '@/composables/useSession'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
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

const {t} = useI18n()
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

const {loading, error, reload: loadData} = useAsyncLoader(async () => {
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
  for (const {ticketId, labelId} of tlm) {
    if (!map.has(ticketId)) map.set(ticketId, [])
    map.get(ticketId)!.push(labelId)
  }
  ticketLabelMap.value = map
  if (!createLaneId.value) {
    const firstAllowed = bd.board.backlogLaneId ?? l.find(la => la.id !== bd.board.backlogLaneId)?.id
    if (firstAllowed) createLaneId.value = String(firstAllowed)
  }
})

const board = computed(() => boardDetail.value?.board ?? null)

const visibleLanes = computed(() => lanes.value.filter(l => !board.value?.backlogLaneId || l.id !== board.value.backlogLaneId))

function ticketsForLane(laneId: number): BoardTicket[] {
  return tickets.value.filter(tt => tt.laneId === laneId).sort((a, b) => a.position - b.position)
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
  return ticketsForLane(laneId).filter(tt => !shouldHideTicket(tt, laneId))
}

function archivedCountForLane(laneId: number): number {
  return ticketsForLane(laneId).filter(tt => shouldHideTicket(tt, laneId)).length
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
    } catch {
    } finally {
      searching.value = false
    }
  }, 300)
}

function openTicketDetail(ticket: BoardTicket) {
  router.push(`/station/federation/boards/${partnerUid.value}/${boardKey.value}/tickets/${ticket.ticketNumber}`)
}

function onSearchPick(ticket: BoardTicket) {
  openTicketDetail(ticket)
  searchQuery.value = ''
  searchResults.value = null
}

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

  const otherTickets = tickets.value.filter(tt => tt.laneId === laneId && tt.id !== ticket.id).sort((a, b) => a.position - b.position)
  otherTickets.splice(pos, 0, ticket)
  const updatedTicket = {
    ...ticket,
    laneId,
    laneEnteredAt: ticket.laneId !== laneId ? new Date().toISOString() : ticket.laneEnteredAt,
  }
  tickets.value = tickets.value.filter(tt => tt.id !== ticket.id).map(tt => {
    const idx = otherTickets.findIndex(ot => ot.id === tt.id)
    return idx >= 0 ? {...tt, position: idx} : tt
  })
  tickets.value.push({...updatedTicket, position: pos})

  if (ticket.laneId === laneId) {
    try {
      await fedReorderTickets(partnerUid.value, boardKey.value, ticket.ticketNumber, {
        laneId,
        orderedIds: otherTickets.map(tt => tt.id),
      })
    } catch {
      await loadData()
    }
  } else {
    try {
      await fedMoveTicket(partnerUid.value, boardKey.value, ticket.ticketNumber, {toLaneId: laneId, position: pos})
    } catch {
      await loadData()
    }
  }
}

function onDragEnd() {
  dragTicket.value = null
  dropLaneId.value = null
  dropPosition.value = null
}

watch([partnerUid, boardKey], loadData)
</script>

<template>
  <ViewContent>
    <Spinner v-if="loading"/>
    <Alert v-else-if="error" variant="error">{{ error }}</Alert>
    <template v-else-if="board">
      <FederatedBoardHeader
          :board-name="board.name"
          :short-key="board.shortKey"
          :is-read-only="isReadOnly"
          :is-full="isFull"
          :can-manage-boards="canManageBoards()"
          v-model:search-query="searchQuery"
          :search-results="searchResults"
          :lane-name="laneName"
          :priority-icon="priorityIcon"
          :priority-color="priorityColor"
          @search-input="onSearchInput"
          @pick-result="onSearchPick"
          @open-override="showOverrideModal = true"
          @open-create="showCreateModal = true"
      />

      <div class="flex items-center gap-2 mb-4 text-sm text-(--text-muted)">
        <font-awesome-icon :icon="['fas', 'share-nodes']"/>
        <span>{{ t('boards.federatedFrom') }}: <strong class="text-(--text)">{{ boardDetail?.stationName }}</strong></span>
      </div>

      <div class="flex flex-col md:flex-row gap-4 md:overflow-x-auto pb-4" style="min-height: 200px">
        <FederatedBoardLane
            v-for="lane in visibleLanes"
            :key="lane.id"
            :lane="lane"
            :short-key="board.shortKey"
            :is-full="isFull"
            :visible-tickets="visibleTicketsForLane(lane.id)"
            :is-last-lane="isLastLane(lane.id)"
            :archived-count="archivedCountForLane(lane.id)"
            :drag-ticket="dragTicket"
            :drop-lane-id="dropLaneId"
            :drop-position="dropPosition"
            :labels-for-ticket="labelsForTicket"
            @dragover="onLaneDragOver"
            @dragleave="onLaneDragLeave"
            @drop="onLaneDrop"
            @ticket-dragstart="onTicketDragStart"
            @ticket-dragend="onDragEnd"
            @open-ticket="openTicketDetail"
        />
      </div>

      <FederatedBoardCreateTicketModal
          v-if="isFull"
          v-model="showCreateModal"
          v-model:title="createTitle"
          v-model:description="createDescription"
          v-model:lane-id="createLaneId"
          v-model:priority="createPriority"
          :lane-options="createLaneOptions"
          :error="createError"
          @create="handleCreateTicket"
      />
    </template>

    <FederatedBoardAccessOverride
        v-if="showOverrideModal"
        v-model="showOverrideModal"
        :partner-uid="partnerUid"
        :board-key="boardKey"
    />
  </ViewContent>
</template>
