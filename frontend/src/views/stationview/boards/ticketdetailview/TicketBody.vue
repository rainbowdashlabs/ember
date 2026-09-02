/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import TicketLeftColumn from './TicketLeftColumn.vue'
import TicketRightColumn from './TicketRightColumn.vue'
import type {
    Board, BoardLane, BoardField, BoardLabel, BoardTicket, BoardChecklistItem,
    BoardTicketLink, BoardTicketTransition, BoardTicketHistoryEntry, BoardComment,
    BoardWeblink, BoardTicketAttachment, BoardTicketKbLink, TicketPriorityName,
    BoardFieldTypeName,
} from '@/api/boards'
import type { MemberCompletion } from '@/api/stationMembers'
import type {PriorityOption, KbSearchResult} from './types'



defineProps<{
    board: Board
    ticket: BoardTicket
    allTickets: BoardTicket[]
    lanes: BoardLane[]
    members: MemberCompletion[]
    assignableMembers: MemberCompletion[]
    allLabels: BoardLabel[]
    ticketLabels: BoardLabel[]
    boardFields: BoardField[]
    priorityOptions: PriorityOption[]
    checklist: BoardChecklistItem[]
    checklistVisible: boolean
    links: BoardTicketLink[]
    weblinks: BoardWeblink[]
    attachments: BoardTicketAttachment[]
    transitions: BoardTicketTransition[]
    history: BoardTicketHistoryEntry[]
    comments: BoardComment[]
    kbLinks: BoardTicketKbLink[]
    kbSearchResults: KbSearchResult[]
    canEdit: boolean
    federated: boolean
    partnerUid?: string
    error: string
}>()

const title = defineModel<string>('title', { default: '' })
const description = defineModel<string>('description', { default: '' })
const newChecklistTitle = defineModel<string>('newChecklistTitle', { default: '' })
const kbSearchQuery = defineModel<string>('kbSearchQuery', { default: '' })
const priority = defineModel<TicketPriorityName>('priority')
const assignedMemberId = defineModel<string>('assignedMemberId', { default: '' })
const dueDate = defineModel<string>('dueDate', { default: '' })
const fieldValues = defineModel<Record<number, unknown>>('fieldValues', { default: () => ({}) })
const showAddLink = defineModel<boolean>('showAddLink', { required: true })
const showAddWeblink = defineModel<boolean>('showAddWeblink', { required: true })
const showKbSearch = defineModel<boolean>('showKbSearch', { required: true })

const emit = defineEmits<{
    saveTicket: []
    reloadDetails: []
    showChecklist: []
    addChecklistItem: []
    toggleChecklistItem: [item: BoardChecklistItem]
    removeChecklistItem: [id: number]
    removeAllChecklistItems: []
    reorderChecklist: [from: number, to: number]
    createComment: [parentId: number | null, content: string]
    updateComment: [commentId: number, content: string]
    deleteComment: [commentId: number]
    uploadFile: [file: File]
    kbSearch: []
    addKbLink: [id: number]
    removeKbLink: [id: number]
    moveTo: [laneId: number]
    toggleLabel: [id: number]
    createLabel: [name: string]
    saveField: [fieldId: number, fieldType: BoardFieldTypeName, value: unknown]
}>()
</script>

<template>
    <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <TicketLeftColumn
            v-model:title="title" v-model:description="description"
            v-model:new-checklist-title="newChecklistTitle" v-model:kb-search-query="kbSearchQuery"
            v-model:show-add-link="showAddLink" v-model:show-add-weblink="showAddWeblink"
            v-model:show-kb-search="showKbSearch"
            :board="board" :ticket="ticket" :all-tickets="allTickets" :lanes="lanes" :members="members"
            :all-labels="allLabels" :priority-options="priorityOptions" :checklist="checklist"
            :checklist-visible="checklistVisible" :links="links" :weblinks="weblinks"
            :attachments="attachments" :transitions="transitions" :history="history" :comments="comments"
            :kb-links="kbLinks" :kb-search-results="kbSearchResults" :can-edit="canEdit"
            :federated="federated" :partner-uid="partnerUid"
            @save-ticket="emit('saveTicket')" @reload-details="emit('reloadDetails')"
            @show-checklist="emit('showChecklist')"
            @add-checklist-item="emit('addChecklistItem')"
            @toggle-checklist-item="emit('toggleChecklistItem', $event)"
            @remove-checklist-item="emit('removeChecklistItem', $event)"
            @remove-all-checklist-items="emit('removeAllChecklistItems')"
            @reorder-checklist="(f, t) => emit('reorderChecklist', f, t)"
            @create-comment="(p, c) => emit('createComment', p, c)"
            @update-comment="(i, c) => emit('updateComment', i, c)"
            @delete-comment="emit('deleteComment', $event)"
            @upload-file="emit('uploadFile', $event)"
            @kb-search="emit('kbSearch')"
            @add-kb-link="emit('addKbLink', $event)"
            @remove-kb-link="emit('removeKbLink', $event)"
        />
        <TicketRightColumn
            v-model:priority="priority" v-model:assigned-member-id="assignedMemberId"
            v-model:due-date="dueDate" v-model:field-values="fieldValues"
            :ticket="ticket" :lanes="lanes" :members="members" :assignable-members="assignableMembers" :all-labels="allLabels"
            :ticket-labels="ticketLabels" :board-fields="boardFields"
            :priority-options="priorityOptions" :can-edit="canEdit" :error="error"
            @move-to="emit('moveTo', $event)" @save-ticket="emit('saveTicket')"
            @toggle-label="emit('toggleLabel', $event)" @create-label="emit('createLabel', $event)"
            @save-field="(id, type, value) => emit('saveField', id, type, value)"
        />
    </div>
</template>
