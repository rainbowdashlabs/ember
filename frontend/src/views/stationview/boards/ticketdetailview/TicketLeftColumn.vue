/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { ref } from 'vue'
import FileUploadButton from '@/components/button/FileUploadButton.vue'
import TicketChecklist from '../boardview/TicketChecklist.vue'
import TicketActivity from '../boardview/TicketActivity.vue'
import TicketLinksSection from '../boardview/TicketLinksSection.vue'
import TicketTitleEditor from './TicketTitleEditor.vue'
import TicketDescriptionEditor from './TicketDescriptionEditor.vue'
import TicketAddMenu from './TicketAddMenu.vue'
import TicketKbLinksSection from './TicketKbLinksSection.vue'
import type {
    Board, BoardLane, BoardTicket, BoardChecklistItem, BoardTicketLink,
    BoardTicketTransition, BoardTicketHistoryEntry, BoardComment,
    BoardWeblink, BoardTicketAttachment, BoardTicketKbLink, BoardLabel,
} from '@/api/boards'
import type { MemberCompletion } from '@/api/stationMembers'
import type {PriorityOption, KbSearchResult} from './types'



defineProps<{
    board: Board
    ticket: BoardTicket
    allTickets: BoardTicket[]
    lanes: BoardLane[]
    members: MemberCompletion[]
    allLabels: BoardLabel[]
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
}>()

const title = defineModel<string>('title', { default: '' })
const description = defineModel<string>('description', { default: '' })
const newChecklistTitle = defineModel<string>('newChecklistTitle', { default: '' })
const kbSearchQuery = defineModel<string>('kbSearchQuery', { default: '' })
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
}>()

const editingTitle = ref(false)
const editingDescription = ref(false)
const fileInputRef = ref<InstanceType<typeof FileUploadButton> | null>(null)

function triggerFilePicker() {
    fileInputRef.value?.inputRef?.click()
}

function onFileSelect(file: File) {
    emit('uploadFile', file)
}
</script>

<template>
    <div class="lg:col-span-2 space-y-4">
        <TicketTitleEditor
            v-model:title="title"
            v-model:editing="editingTitle"
            :can-edit="canEdit"
            @save="emit('saveTicket')"
        />
        <TicketAddMenu
            v-if="canEdit"
            :can-add-checklist="!checklistVisible"
            @add-checklist="emit('showChecklist')"
            @add-link="showAddLink = true"
            @add-weblink="showAddWeblink = true"
            @add-attachment="triggerFilePicker"
            @add-kb-link="showKbSearch = true"
        />
        <div v-if="canEdit" class="hidden"><FileUploadButton ref="fileInputRef" @select="onFileSelect" /></div>
        <TicketDescriptionEditor
            v-model:description="description"
            v-model:editing="editingDescription"
            :can-edit="canEdit"
            @save="emit('saveTicket')"
        />
        <TicketChecklist
            v-if="checklistVisible"
            v-model:new-title="newChecklistTitle"
            :checklist="checklist"
            :readonly="!canEdit"
            @toggle="emit('toggleChecklistItem', $event)"
            @add="emit('addChecklistItem')"
            @remove="emit('removeChecklistItem', $event)"
            @remove-all="emit('removeAllChecklistItems')"
            @reorder="(from, to) => emit('reorderChecklist', from, to)"
        />
        <TicketLinksSection
            v-model:show-add-link="showAddLink"
            v-model:show-add-weblink="showAddWeblink"
            :board-key="board.shortKey"
            :ticket-number="ticket.ticketNumber"
            :short-key="board.shortKey"
            :links="links"
            :weblinks="weblinks"
            :attachments="attachments"
            :all-tickets="allTickets"
            :lanes="lanes"
            :members="members"
            :priority-options="priorityOptions"
            :readonly="!canEdit"
            :partner-uid="partnerUid"
            @reload="emit('reloadDetails')"
            @add-file="triggerFilePicker"
        />
        <TicketKbLinksSection
            v-if="kbLinks.length > 0 || showKbSearch"
            v-model:search-query="kbSearchQuery"
            :kb-links="kbLinks"
            :show-search="showKbSearch"
            :search-results="kbSearchResults"
            :readonly="!canEdit"
            @search="emit('kbSearch')"
            @add="emit('addKbLink', $event)"
            @remove="emit('removeKbLink', $event)"
        />
        <TicketActivity
            :comments="comments"
            :transitions="transitions"
            :history="history"
            :lanes="lanes"
            :labels="allLabels"
            :members="members"
            :readonly="!canEdit"
            :federated="federated"
            @create-comment="(parentId, content) => emit('createComment', parentId, content)"
            @update-comment="(id, content) => emit('updateComment', id, content)"
            @delete-comment="emit('deleteComment', $event)"
        />
    </div>
</template>
