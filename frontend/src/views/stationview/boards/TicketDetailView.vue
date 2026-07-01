/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import Alert from '@/components/feedback/Alert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Modal from '@/components/feedback/Modal.vue'
import TicketHeaderBar from './ticketdetailview/TicketHeaderBar.vue'
import TicketBody from './ticketdetailview/TicketBody.vue'
import { knowledgeBase, boards } from '@/api'
import type { MemberCompletion } from '@/api/stationMembers'
import type {
    Board, BoardLane, BoardField, BoardLabel, BoardTicket, BoardChecklistItem,
    BoardTicketLink, BoardTicketTransition, BoardTicketHistoryEntry, BoardComment,
    BoardWeblink, BoardTicketAttachment, BoardTicketKbLink,
} from '@/api/boards'
import { TicketPriority, type TicketPriorityName } from '@/api/boards'
import { useSession } from '@/composables/useSession'
import { useBoardApi } from '@/composables/useBoardApi'
import { useAsyncLoader } from '@/composables/useAsyncLoader'

const { t } = useI18n()
const router = useRouter()
const { sessionInfo } = useSession()
const api = useBoardApi()

const boardKey = api.boardKey
const ticketNumber = api.ticketNumber

const board = ref<Board | null>(null)
const ticket = ref<BoardTicket | null>(null)
const lanes = ref<BoardLane[]>([])
const members = ref<MemberCompletion[]>([])
const canEdit = ref(false)

const title = ref('')
const description = ref('')
const priority = ref<TicketPriorityName>(TicketPriority.MEDIUM)
const assignedMemberId = ref('')
const dueDate = ref('')

const checklist = ref<BoardChecklistItem[]>([])
const newChecklistTitle = ref('')
const links = ref<BoardTicketLink[]>([])
const transitions = ref<BoardTicketTransition[]>([])
const ticketHistory = ref<BoardTicketHistoryEntry[]>([])
const kbLinks = ref<BoardTicketKbLink[]>([])
const showKbSearch = ref(false)
const kbSearchQuery = ref('')
interface KbSearchResult {
  id: number
  title: string
  path: string
}

const kbSearchResults = ref<KbSearchResult[]>([])
const comments = ref<BoardComment[]>([])
const weblinks = ref<BoardWeblink[]>([])
const attachments = ref<BoardTicketAttachment[]>([])

const showDeleteModal = ref(false)
const showChecklist = ref(false)
const showAddLink = ref(false)
const showAddWeblink = ref(false)

const priorityOptions = computed(() => [
    { value: TicketPriority.HIGHEST, label: t('boards.priorityHighest'), icon: ['fas', 'angles-up'], color: 'text-red-500' },
    { value: TicketPriority.HIGH, label: t('boards.priorityHigh'), icon: ['fas', 'angle-up'], color: 'text-orange-500' },
    { value: TicketPriority.MEDIUM, label: t('boards.priorityMedium'), icon: ['fas', 'equals'], color: 'text-yellow-500' },
    { value: TicketPriority.LOW, label: t('boards.priorityLow'), icon: ['fas', 'angle-down'], color: 'text-blue-400' },
    { value: TicketPriority.LOWEST, label: t('boards.priorityLowest'), icon: ['fas', 'angles-down'], color: 'text-gray-400' },
])
const isWatching = ref(false)

const allTickets = ref<BoardTicket[]>([])
const boardFields = ref<BoardField[]>([])
const fieldValues = ref<Record<number, unknown>>({})
const allLabels = ref<BoardLabel[]>([])
const ticketLabels = ref<BoardLabel[]>([])

const {loading, error, reload} = useAsyncLoader(async () => {
    const [boardResult, tk, l, m, bf] = await Promise.all([
        api.getBoard(),
        api.getTicket(),
        api.getLanes(),
        api.getMembers(),
        api.getFields(),
    ])
    board.value = boardResult.board as Board
    ticket.value = tk
    lanes.value = l
    members.value = m
    boardFields.value = bf
    canEdit.value = boardResult.canEdit
    allLabels.value = await api.getLabels()
    title.value = tk.title
    description.value = tk.description ?? ''
    priority.value = tk.priority
    assignedMemberId.value = tk.assignee ? (members.value.find(m => m.memberUid === tk.assignee?.memberUid)?.id?.toString() ?? '') : ''
    dueDate.value = tk.dueDate ?? ''
    await loadDetails()
    allTickets.value = await api.listTickets()
    if (sessionInfo.value?.member) {
        isWatching.value = (await api.getWatchers())
            .includes(sessionInfo.value.member.id)
    }
})

async function loadDetails() {
    try {
        const [cl, li, tr, hi, co, wl, at, fv] = await Promise.all([
            api.getChecklist(),
            api.getLinks(),
            api.getTransitions(),
            api.getHistory(),
            api.getComments(),
            api.getWeblinks(),
            api.getAttachments(),
            api.getFieldValues(),
        ])
        checklist.value = cl
        links.value = li
        transitions.value = tr
        ticketHistory.value = hi
        comments.value = co
        weblinks.value = wl
        attachments.value = at
        fieldValues.value = Object.fromEntries(fv.map(v => [v.fieldId, !v.value ? null : v.fieldType === 'LANE_ASSIGNEE' ? (v.value.memberId ?? null) : (v.value.value ?? null)]))
        ticketLabels.value = await api.getTicketLabels()
        kbLinks.value = await api.getKbLinks()
    } catch { /* ignore */ }
}

async function saveTicket() { error.value = ''; try { await api.updateTicket({ title: title.value, description: description.value || null, assignedMemberId: assignedMemberId.value ? Number(assignedMemberId.value) : null, priority: priority.value, dueDate: dueDate.value || null }); ticket.value = await api.getTicket(); await loadDetails() } catch { error.value = t('common.error') } }
async function deleteTicketFn() { try { await api.deleteTicket(); await router.push(api.backRoute.value) } catch { error.value = t('common.error') } }
async function moveTo(laneId: number) { try { await api.moveTicket({ toLaneId: laneId, position: 0 }); ticket.value = await api.getTicket(); await loadDetails() } catch { /* ignore */ } }
async function addChecklistItem() { if (!newChecklistTitle.value.trim()) return; try { await api.addChecklistItem({ title: newChecklistTitle.value.trim() }); newChecklistTitle.value = ''; await loadDetails() } catch { /* ignore */ } }
async function toggleChecklistItem(item: BoardChecklistItem) { try { await api.updateChecklistItem(item.id, { title: item.title, checked: !item.checked }); await loadDetails() } catch { /* ignore */ } }
async function reorderChecklist(fromIndex: number, toIndex: number) { const items = [...checklist.value]; const [moved] = items.splice(fromIndex, 1); items.splice(toIndex, 0, moved); checklist.value = items; try { await api.reorderChecklist({ orderedIds: items.map(i => i.id) }) } catch { await loadDetails() } }
async function removeAllChecklistItems() { try { for (const item of checklist.value) { await api.deleteChecklistItem(item.id) }; showChecklist.value = false; await loadDetails() } catch { /* ignore */ } }
async function removeChecklistItem(itemId: number) { try { await api.deleteChecklistItem(itemId); await loadDetails() } catch { /* ignore */ } }
async function createComment(parentId: number | null, content: string) { try { await api.createComment({ parentId, content }); await loadDetails() } catch { /* ignore */ } }
async function updateComment(commentId: number, content: string) { try { await api.updateComment(commentId, { content }); await loadDetails() } catch { /* ignore */ } }

async function saveFieldValue(fieldId: number, fieldType: boards.BoardFieldTypeName, value: unknown) {
    try {
        if (value === null || value === undefined || value === '') {
            await api.deleteFieldValue(fieldId)
            delete fieldValues.value[fieldId]
        } else {
            await api.setFieldValue(fieldId, fieldType, value)
            fieldValues.value[fieldId] = value
        }
    } catch { /* ignore */ }
}

let kbSearchTimeout: ReturnType<typeof setTimeout> | null = null
function onKbSearch() { if (kbSearchTimeout) clearTimeout(kbSearchTimeout); if (!kbSearchQuery.value.trim()) { kbSearchResults.value = []; return }; kbSearchTimeout = setTimeout(async () => { try { const results = await knowledgeBase.search(kbSearchQuery.value.trim(), { federated: false }); kbSearchResults.value = results.map(r => ({ id: r.file.id, title: r.file.name, path: r.folderPath })).filter(r => !kbLinks.value.some(l => l.kbFileId === r.id)) } catch { /* ignore */ } }, 300) }
async function addKbLinkFn(kbFileId: number) { try { await boards.addKbLink(boardKey.value, ticketNumber.value, kbFileId); kbLinks.value = await api.getKbLinks(); kbSearchQuery.value = ''; kbSearchResults.value = []; showKbSearch.value = false } catch { /* ignore */ } }
async function removeKbLinkFn(linkId: number) { try { await boards.removeKbLink(boardKey.value, ticketNumber.value, linkId); kbLinks.value = await api.getKbLinks() } catch { /* ignore */ } }

async function createAndAddLabel(name: string) { try { const label = await api.createLabel({ name }); allLabels.value = await api.getLabels(); await api.addTicketLabel(label.id); ticketLabels.value = await api.getTicketLabels() } catch { /* ignore */ } }
async function toggleLabel(labelId: number) {
    try {
        if (ticketLabels.value.some(l => l.id === labelId)) { await api.removeTicketLabel(labelId) }
        else { await api.addTicketLabel(labelId) }
        ticketLabels.value = await api.getTicketLabels()
        ticketHistory.value = await api.getHistory()
        kbLinks.value = await api.getKbLinks()
    } catch { /* ignore */ }
}

async function handleFileUpload(file: File) {
    try {
        await api.uploadAttachment(file)
        await loadDetails()
    } catch { /* ignore */ }
}

async function toggleWatch() {
    try {
        if (isWatching.value) {
            await api.unwatchTicket()
        } else {
            await api.watchTicket()
        }
        isWatching.value = !isWatching.value
    } catch { /* ignore */ }
}

async function deleteCommentFn(commentId: number) {
    try {
        await api.deleteComment(commentId)
        await loadDetails()
    } catch { /* ignore */ }
}

const checklistVisible = computed(() => checklist.value.length > 0 || showChecklist.value || newChecklistTitle.value !== '')

watch(ticketNumber, reload)
</script>

<template>
    <ViewContent
        :title="t('pages.ticket-detail.title')"
        :subtitle="t('pages.ticket-detail.subtitle')"
    >
        <Spinner v-if="loading" />
        <Alert v-else-if="error && !ticket" variant="error">{{ error }}</Alert>
        <template v-else-if="board && ticket">
            <TicketHeaderBar
                :short-key="board.shortKey"
                :ticket-number="ticket.ticketNumber"
                :is-watching="isWatching"
                :can-edit="canEdit"
                @back="router.push(api.backRoute.value)"
                @toggle-watch="toggleWatch"
                @request-delete="showDeleteModal = true"
            />
            <TicketBody
                v-model:title="title" v-model:description="description"
                v-model:new-checklist-title="newChecklistTitle" v-model:kb-search-query="kbSearchQuery"
                v-model:priority="priority" v-model:assigned-member-id="assignedMemberId"
                v-model:due-date="dueDate" v-model:field-values="fieldValues"
                v-model:show-add-link="showAddLink" v-model:show-add-weblink="showAddWeblink"
                v-model:show-kb-search="showKbSearch"
                :board="board" :ticket="ticket" :all-tickets="allTickets" :lanes="lanes" :members="members"
                :all-labels="allLabels" :ticket-labels="ticketLabels" :board-fields="boardFields"
                :priority-options="priorityOptions" :checklist="checklist" :checklist-visible="checklistVisible"
                :links="links" :weblinks="weblinks" :attachments="attachments" :transitions="transitions"
                :history="ticketHistory" :comments="comments" :kb-links="kbLinks"
                :kb-search-results="kbSearchResults" :can-edit="canEdit"
                :federated="api.isFederated.value" :partner-uid="api.partnerUid.value" :error="error"
                @save-ticket="saveTicket" @reload-details="loadDetails"
                @show-checklist="showChecklist = true"
                @add-checklist-item="addChecklistItem"
                @toggle-checklist-item="toggleChecklistItem"
                @remove-checklist-item="removeChecklistItem"
                @remove-all-checklist-items="removeAllChecklistItems"
                @reorder-checklist="reorderChecklist"
                @create-comment="createComment" @update-comment="updateComment"
                @delete-comment="deleteCommentFn" @upload-file="handleFileUpload"
                @kb-search="onKbSearch" @add-kb-link="addKbLinkFn" @remove-kb-link="removeKbLinkFn"
                @move-to="moveTo" @toggle-label="toggleLabel" @create-label="createAndAddLabel"
                @save-field="saveFieldValue"
            />
            <Modal v-model="showDeleteModal">
                <SubHeader class="mb-4">{{ t('common.delete') }}</SubHeader>
                <p class="mb-4">Soll dieses Ticket wirklich gelöscht werden?</p>
                <div class="flex justify-end gap-2">
                    <DeleteButton @click="deleteTicketFn">{{ t('common.delete') }}</DeleteButton>
                </div>
            </Modal>
        </template>
    </ViewContent>
</template>
