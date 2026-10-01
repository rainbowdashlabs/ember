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
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import FederatedBoardAccessOverride from '@/views/stationview/federation/FederatedBoardAccessOverride.vue'
import FederatedBoardHeader from '@/views/stationview/federation/federatedboardview/FederatedBoardHeader.vue'
import KanbanBoard from '@/components/kanban/KanbanBoard.vue'
import {boardLanes} from '@/components/kanban/kanbanLanes'
import FederatedBoardCreateTicketModal
  from '@/views/stationview/federation/federatedboardview/FederatedBoardCreateTicketModal.vue'
import {TicketPriority, type TicketPriorityName} from '@/api/boards'
import type {BoardLabel, BoardLane, FederatedBoardDetail, TicketSummary} from '@/api/generated/schema'
import {priorityIcon, priorityColor} from '@/util/ticketPriority'
import {useSession} from '@/composables/useSession'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useTicketMoves} from '@/composables/useTicketMoves'
import {reportCaughtError} from '@/util/devErrorReporter'
import {
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
const tickets = ref<TicketSummary[]>([])
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
const searchResults = ref<TicketSummary[] | null>(null)
const searching = ref(false)

const {loading, failure, reload: loadData} = useAsyncLoader(async () => {
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

/**
 * The partner's board by its own name at the head of the page. The static words stand until the
 * partner has answered, and where the board could not be fetched at all.
 */
const pageTitle = computed(() => board.value?.name || t('pages.federated-board-view.title'))

/** Once the name is the title, the line under it is what says whose board this is. */
const pageSubtitle = computed(() => boardDetail.value?.stationName
    || t('pages.federated-board-view.subtitle'))

const visibleLanes = computed(() => boardLanes(lanes.value, board.value?.backlogLaneId ?? null))

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

function ticketPage(ticket: TicketSummary) {
  return `/station/federation/boards/${partnerUid.value}/${boardKey.value}/tickets/${ticket.ticketNumber}`
}

function openTicketDetail(ticket: TicketSummary) {
  router.push(ticketPage(ticket))
}

/** A hit opens as the link it is, which leaves the search itself to be put away. */
function onSearchPick() {
  searchQuery.value = ''
  searchResults.value = null
}

const {failure: createFailure, run: runCreateTicket} = useAsyncAction(async () => {
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
})

function handleCreateTicket() {
  createValidationError.value = ''
  if (!createTitle.value.trim()) {
    createValidationError.value = t('common.requiredField')
    return
  }
  void runCreateTicket()
}

const {moveTicket} = useTicketMoves(tickets, {
  reorder: (ticketNumber, payload) => fedReorderTickets(partnerUid.value, boardKey.value, ticketNumber, payload),
  move: (ticketNumber, payload) => fedMoveTicket(partnerUid.value, boardKey.value, ticketNumber, payload),
}, loadData)

watch([partnerUid, boardKey], loadData)
</script>

<template>
  <ViewContent
      :title="pageTitle"
      :subtitle="pageSubtitle"
  >
    <Spinner v-if="loading"/>
    <FailureAlert v-else-if="failure" :failure="failure"/>
    <template v-else-if="board">
      <FederatedBoardHeader
          v-model:search-query="searchQuery"
          :board-name="board.name"
          :short-key="board.shortKey"
          :is-read-only="isReadOnly"
          :is-full="isFull"
          :can-manage-boards="canManageBoards()"
          :search-results="searchResults"
          :ticket-page="ticketPage"
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

      <KanbanBoard
          :board="board"
          :lanes="lanes"
          :tickets="tickets"
          :labels-for-ticket="labelsForTicket"
          :read-only="!isFull"
          @move="moveTicket"
          @open="openTicketDetail"
      />

      <FederatedBoardCreateTicketModal
          v-if="isFull"
          v-model="showCreateModal"
          v-model:title="createTitle"
          v-model:description="createDescription"
          v-model:lane-id="createLaneId"
          v-model:priority="createPriority"
          :lane-options="createLaneOptions"
          :validation-error="createValidationError"
          :failure="createFailure"
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
