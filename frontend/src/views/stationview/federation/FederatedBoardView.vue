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
import {TicketPriority, type BoardLabel, type BoardLane, type BoardTicket, type TicketPriorityName} from '@/api/boards'
import {priorityIcon, priorityColor} from '@/util/ticketPriority'
import {useSession} from '@/composables/useSession'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useBoardDragAndDrop} from '@/composables/useBoardDragAndDrop'
import {reportCaughtError} from '@/util/devErrorReporter'
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
const createValidationError = ref('')

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
    } catch (e) {
      reportCaughtError(e, 'federated ticket search')
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

const {error: createApiError, run: runCreateTicket} = useAsyncAction(async () => {
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
}, {formatError: () => t('common.error')})

const createError = computed(() => createValidationError.value || createApiError.value)

function handleCreateTicket() {
  createValidationError.value = ''
  if (!createTitle.value.trim()) {
    createValidationError.value = t('common.requiredField')
    return
  }
  void runCreateTicket()
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
  reorder: (ticketNumber, payload) => fedReorderTickets(partnerUid.value, boardKey.value, ticketNumber, payload),
  move: (ticketNumber, payload) => fedMoveTicket(partnerUid.value, boardKey.value, ticketNumber, payload),
}, loadData, () => !isReadOnly.value)

watch([partnerUid, boardKey], loadData)
</script>

<template>
  <ViewContent
      :title="t('pages.federated-board-view.title')"
      :subtitle="t('pages.federated-board-view.subtitle')"
  >
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
