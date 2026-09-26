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
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Modal from '@/components/feedback/Modal.vue'
import TicketHeaderBar from './ticketdetailview/TicketHeaderBar.vue'
import TicketBody from './ticketdetailview/TicketBody.vue'
import { knowledgeBase, boards } from '@/api'
import type { MemberCompletion } from '@/api/stationMembers'
import {TicketPriority, type Board, type BoardChecklistItem, type BoardComment, type BoardField, type BoardLabel, type BoardLane, type BoardTicket, type BoardTicketAttachment, type BoardTicketHistoryEntry, type BoardTicketKbLink, type BoardTicketLink, type BoardTicketTransition, type BoardWeblink, type TicketPriorityName} from '@/api/boards'
import { useSession } from '@/composables/useSession'
import { useBoardApi } from '@/composables/useBoardApi'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import { useAsyncAction } from '@/composables/useAsyncAction'
import { useConfirmDelete } from '@/composables/useConfirmDelete'
import { moveWithin } from '@/util/reorder'
import { describeFailure, type Failure } from '@/util/failure'
import { priorityColor, priorityIcon, priorityOptions } from '@/util/ticketPriority'
import type { PriorityOption } from './ticketdetailview/types'

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
const assignableMembers = ref<MemberCompletion[]>([])
const canEdit = ref(false)

/**
 * The ticket's identifier and its headline at the head of the page, so that the tab, the history and
 * a bookmark say which ticket is open instead of saying "Ticket" over and over. The word stands
 * until board and ticket have both arrived, and where either failed to load.
 */
const pageTitle = computed(() => board.value && ticket.value
    ? `${board.value.shortKey}-${ticket.value.ticketNumber} ${ticket.value.title}`
    : t('pages.ticket-detail.title'))

/** With the ticket named above it, the line under it is where the board it lives on goes. */
const pageSubtitle = computed(() => board.value?.name || t('pages.ticket-detail.subtitle'))

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

const showChecklist = ref(false)
const showAddLink = ref(false)
const showAddWeblink = ref(false)

const priorityChoices = computed<PriorityOption[]>(() => priorityOptions(t).reverse().map(option => {
    const value = option.value as TicketPriorityName
    return { ...option, icon: priorityIcon(value), color: priorityColor(value) }
}))
const isWatching = ref(false)

const allTickets = ref<BoardTicket[]>([])
const boardFields = ref<BoardField[]>([])
const fieldValues = ref<Record<number, unknown>>({})
const allLabels = ref<BoardLabel[]>([])
const ticketLabels = ref<BoardLabel[]>([])

const {loading, failure: loadFailure, reload} = useAsyncLoader(async () => {
    const [boardResult, tk, l, m, am, bf] = await Promise.all([
        api.getBoard(),
        api.getTicket(),
        api.getLanes(),
        api.getMembers(),
        api.getAssignableMembers(),
        api.getFields(),
    ])
    board.value = boardResult.board as Board
    ticket.value = tk
    lanes.value = l
    members.value = m
    assignableMembers.value = am
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

/**
 * Fetches everything hanging off the ticket. Lets a failure out, because the callers differ in what
 * it means to them: during the first load it is the page not arriving, and after a change it is the
 * change having worked while the screen stayed behind.
 */
async function loadDetails() {
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
}

/**
 * What the reader's last action ran into, which every one of these used to throw away.
 *
 * <p>A checklist item that was not added, a comment that was not posted and a lane the ticket would
 * not move to all looked exactly like nothing having happened: no word, no mark, nothing. The reader
 * pressed again, and where the write had in fact gone through and only the refresh had failed, the
 * ticket then carried it twice.
 */
const actionFailure = ref<Failure | null>(null)

/**
 * Changing the ticket and then catching the screen up, which are two things and not one. The second
 * failing means the change worked and the page is merely behind, which is said in those words and
 * asks the reader for nothing.
 *
 * @param change what to do
 * @param after  how to catch the screen up, where fetching the details again is not the whole of it
 */
async function act(change: () => Promise<unknown>, after: () => Promise<unknown> = loadDetails) {
    actionFailure.value = null
    try {
        await change()
    } catch (e) {
        actionFailure.value = describeFailure(e, t)
        return
    }
    try {
        await after()
    } catch (e) {
        actionFailure.value = {...describeFailure(e, t), message: t('failure.staleAfterAction')}
    }
}

/** The details again, for the template, which has nowhere to put a rejection. */
async function refreshDetails() {
    try {
        await loadDetails()
    } catch (e) {
        actionFailure.value = describeFailure(e, t)
    }
}

const {failure: saveFailure, run: runSaveTicket} = useAsyncAction(async () => {
    await api.updateTicket({ title: title.value, description: description.value || null, assignedMemberId: assignedMemberId.value ? Number(assignedMemberId.value) : null, priority: priority.value, dueDate: dueDate.value || null })
    ticket.value = await api.getTicket()
    await loadDetails()
}, {coalesce: true})

function saveTicket() { actionFailure.value = null; void runSaveTicket() }

const {show: showDeleteModal, requestDelete: requestDeleteTicket, confirm: confirmDeleteTicket} = useConfirmDelete<BoardTicket>({
    onDelete: async () => {
        await api.deleteTicket()
        await router.push(api.backRoute.value)
    },
    failure: actionFailure,
})
async function moveTo(laneId: number) { await act(() => api.moveTicket({ toLaneId: laneId, position: 0 }), async () => { ticket.value = await api.getTicket(); await loadDetails() }) }
async function addChecklistItem() { if (!newChecklistTitle.value.trim()) return; const title = newChecklistTitle.value.trim(); await act(() => api.addChecklistItem({ title }), async () => { newChecklistTitle.value = ''; await loadDetails() }) }
async function toggleChecklistItem(item: BoardChecklistItem) { await act(() => api.updateChecklistItem(item.id, { title: item.title, checked: !item.checked })) }
async function reorderChecklist(fromIndex: number, toIndex: number) { const items = moveWithin(checklist.value, fromIndex, toIndex); checklist.value = items; await act(() => api.reorderChecklist({ orderedIds: items.map(i => i.id) }), () => Promise.resolve()) }
async function removeAllChecklistItems() { const items = [...checklist.value]; await act(async () => { for (const item of items) { await api.deleteChecklistItem(item.id) } }, async () => { showChecklist.value = false; await loadDetails() }) }
async function removeChecklistItem(itemId: number) { await act(() => api.deleteChecklistItem(itemId)) }
async function createComment(parentId: number | null, content: string) { await act(() => api.createComment({ parentId, content })) }
async function updateComment(commentId: number, content: string) { await act(() => api.updateComment(commentId, { content })) }

async function saveFieldValue(fieldId: number, fieldType: boards.BoardFieldTypeName, value: unknown) {
    const empty = value === null || value === undefined || value === ''
    await act(
        () => (empty ? api.deleteFieldValue(fieldId) : api.setFieldValue(fieldId, fieldType, value)),
        () => {
            if (empty) delete fieldValues.value[fieldId]
            else fieldValues.value[fieldId] = value
            return Promise.resolve()
        },
    )
}

let kbSearchTimeout: ReturnType<typeof setTimeout> | null = null
/**
 * Searches the wiki as the reader types. A failed search used to leave the list empty, which reads as
 * there being no such article, so it says what happened instead.
 */
function onKbSearch() {
    if (kbSearchTimeout) clearTimeout(kbSearchTimeout)
    if (!kbSearchQuery.value.trim()) { kbSearchResults.value = []; return }
    kbSearchTimeout = setTimeout(async () => {
        actionFailure.value = null
        try {
            const results = await knowledgeBase.search(kbSearchQuery.value.trim(), { federated: false })
            kbSearchResults.value = results
                .map(r => ({ id: r.file.id, title: r.file.name, path: r.folderPath }))
                .filter(r => !kbLinks.value.some(l => l.kbFileId === r.id))
        } catch (e) {
            kbSearchResults.value = []
            actionFailure.value = {...describeFailure(e, t), message: t('boards.kbSearchFailed')}
        }
    }, 300)
}
async function addKbLinkFn(kbFileId: number) { await act(() => boards.addKbLink(boardKey.value, ticketNumber.value, kbFileId), async () => { kbLinks.value = await api.getKbLinks(); kbSearchQuery.value = ''; kbSearchResults.value = []; showKbSearch.value = false }) }
async function removeKbLinkFn(linkId: number) { await act(() => boards.removeKbLink(boardKey.value, ticketNumber.value, linkId), async () => { kbLinks.value = await api.getKbLinks() }) }

async function createAndAddLabel(name: string) {
    await act(
        async () => { const label = await api.createLabel({ name }); await api.addTicketLabel(label.id) },
        async () => { allLabels.value = await api.getLabels(); ticketLabels.value = await api.getTicketLabels() },
    )
}
async function toggleLabel(labelId: number) {
    const attached = ticketLabels.value.some(l => l.id === labelId)
    await act(
        () => (attached ? api.removeTicketLabel(labelId) : api.addTicketLabel(labelId)),
        async () => {
            ticketLabels.value = await api.getTicketLabels()
            ticketHistory.value = await api.getHistory()
            kbLinks.value = await api.getKbLinks()
        },
    )
}

/**
 * Takes the picked files one after another, because the endpoint carries one file per request, and
 * reloads once at the end rather than after each: a ticket given five attachments would otherwise
 * fetch its own details five times.
 */
async function handleFileUpload(files: File[]) {
    await act(async () => {
        for (const file of files) {
            await api.uploadAttachment(file)
        }
    })
}

async function toggleWatch() {
    const watching = isWatching.value
    await act(
        () => (watching ? api.unwatchTicket() : api.watchTicket()),
        () => { isWatching.value = !watching; return Promise.resolve() },
    )
}

async function deleteCommentFn(commentId: number) { await act(() => api.deleteComment(commentId)) }

const checklistVisible = computed(() => checklist.value.length > 0 || showChecklist.value || newChecklistTitle.value !== '')

watch(ticketNumber, reload)
</script>

<template>
    <ViewContent
        :title="pageTitle"
        :subtitle="pageSubtitle"
    >
        <Spinner v-if="loading" />
        <FailureAlert v-else-if="loadFailure && !ticket" :failure="loadFailure"/>
        <template v-else-if="board && ticket">
            <TicketHeaderBar
                :short-key="board.shortKey"
                :ticket-number="ticket.ticketNumber"
                :is-watching="isWatching"
                :can-edit="canEdit"
                @back="router.push(api.backRoute.value)"
                @toggle-watch="toggleWatch"
                @request-delete="requestDeleteTicket(ticket!)"
            />
            <TicketBody
                v-model:title="title" v-model:description="description"
                v-model:new-checklist-title="newChecklistTitle" v-model:kb-search-query="kbSearchQuery"
                v-model:priority="priority" v-model:assigned-member-id="assignedMemberId"
                v-model:due-date="dueDate" v-model:field-values="fieldValues"
                v-model:show-add-link="showAddLink" v-model:show-add-weblink="showAddWeblink"
                v-model:show-kb-search="showKbSearch"
                :board="board" :ticket="ticket" :all-tickets="allTickets" :lanes="lanes" :members="members"
                :assignable-members="assignableMembers"
                :all-labels="allLabels" :ticket-labels="ticketLabels" :board-fields="boardFields"
                :priority-options="priorityChoices" :checklist="checklist" :checklist-visible="checklistVisible"
                :links="links" :weblinks="weblinks" :attachments="attachments" :transitions="transitions"
                :history="ticketHistory" :comments="comments" :kb-links="kbLinks"
                :kb-search-results="kbSearchResults" :can-edit="canEdit"
                :federated="api.isFederated.value" :partner-uid="api.partnerUid.value" :failure="actionFailure ?? saveFailure ?? loadFailure"
                @save-ticket="saveTicket" @reload-details="refreshDetails"
                @show-checklist="showChecklist = true"
                @add-checklist-item="addChecklistItem"
                @toggle-checklist-item="toggleChecklistItem"
                @remove-checklist-item="removeChecklistItem"
                @remove-all-checklist-items="removeAllChecklistItems"
                @reorder-checklist="reorderChecklist"
                @create-comment="createComment" @update-comment="updateComment"
                @delete-comment="deleteCommentFn" @upload-files="handleFileUpload"
                @kb-search="onKbSearch" @add-kb-link="addKbLinkFn" @remove-kb-link="removeKbLinkFn"
                @move-to="moveTo" @toggle-label="toggleLabel" @create-label="createAndAddLabel"
                @save-field="saveFieldValue"
            />
            <Modal v-model="showDeleteModal">
                <SubHeader class="mb-4">{{ t('common.delete') }}</SubHeader>
                <p class="mb-4">Soll dieses Ticket wirklich gelöscht werden?</p>
                <div class="flex justify-end gap-2">
                    <DeleteButton @click="confirmDeleteTicket">{{ t('common.delete') }}</DeleteButton>
                </div>
            </Modal>
        </template>
    </ViewContent>
</template>
