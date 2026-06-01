/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import FileUploadButton from '@/components/button/FileUploadButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import MarkdownEditor from '@/components/input/MarkdownEditor.vue'
import { marked } from 'marked'
import SelectInput from '@/components/input/select/SelectInput.vue'
import IconSelectInput from '@/components/input/select/IconSelectInput.vue'
import MemberSelectInput from '@/components/input/select/MemberSelectInput.vue'
import { contrastTextColor } from '@/theme/contrast'
import NumberInput from '@/components/input/number/NumberInput.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import LabelSelectInput from '@/components/input/select/LabelSelectInput.vue'
import TicketChecklist from './boardview/TicketChecklist.vue'
import TicketActivity from './boardview/TicketActivity.vue'
import TicketLinksSection from './boardview/TicketLinksSection.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import Alert from '@/components/feedback/Alert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Modal from '@/components/feedback/Modal.vue'
import UserAvatar from '@/components/avatar/UserAvatar.vue'
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
const loading = ref(true)
const error = ref('')
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
const kbSearchResults = ref<{ id: number; title: string; path: string }[]>([])
const comments = ref<BoardComment[]>([])
const weblinks = ref<BoardWeblink[]>([])
const attachments = ref<BoardTicketAttachment[]>([])

const showDeleteModal = ref(false)
const showAddMenu = ref(false)
const addMenuRef = ref<HTMLElement | null>(null)
const showChecklist = ref(false)
const showAddLink = ref(false)
const showAddWeblink = ref(false)
const editingTitle = ref(false); const editingDescription = ref(false)
const editingLane = ref(false)
const editingPriority = ref(false)
const editingAssignee = ref(false)
const editingDueDate = ref(false)
const showTicketMenu = ref(false)

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

async function loadData() {
    loading.value = true
    try {
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
        assignedMemberId.value = tk.assignedMemberId?.toString() ?? ''
        dueDate.value = tk.dueDate ?? ''
        await loadDetails()
        allTickets.value = await api.listTickets()
        if (sessionInfo.value?.member) {
            isWatching.value = (await api.getWatchers())
                .includes(sessionInfo.value.member.id)
        }
    } catch {
        error.value = t('common.error')
    } finally {
        loading.value = false
    }
}

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

// -- Field values --

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

// -- KB Links --
let kbSearchTimeout: ReturnType<typeof setTimeout> | null = null
function onKbSearch() { if (kbSearchTimeout) clearTimeout(kbSearchTimeout); if (!kbSearchQuery.value.trim()) { kbSearchResults.value = []; return }; kbSearchTimeout = setTimeout(async () => { try { const results = await knowledgeBase.search(kbSearchQuery.value.trim(), { federated: false }); kbSearchResults.value = results.map(r => ({ id: r.file.id, title: r.file.name, path: r.folderPath })).filter(r => !kbLinks.value.some(l => l.kbFileId === r.id)) } catch { /* ignore */ } }, 300) }
async function addKbLinkFn(kbFileId: number) { try { await boards.addKbLink(boardKey.value, ticketNumber.value, kbFileId); kbLinks.value = await api.getKbLinks(); kbSearchQuery.value = ''; kbSearchResults.value = []; showKbSearch.value = false } catch { /* ignore */ } }
async function removeKbLinkFn(linkId: number) { try { await boards.removeKbLink(boardKey.value, ticketNumber.value, linkId); kbLinks.value = await api.getKbLinks() } catch { /* ignore */ } }

// -- Labels --
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
// -- File upload --

const fileInputRef = ref<InstanceType<typeof FileUploadButton> | null>(null)

async function handleFileUpload(file: File) {
    try {
        await api.uploadAttachment(file)
        await loadDetails()
    } catch { /* ignore */ }
}

// -- Watchers --

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

function renderMarkdown(md: string): string { try { return marked.parse(md) as string } catch { return md } }
function formatDate(iso: string): string { return new Date(iso).toLocaleString('de-DE', { day: '2-digit', month: '2-digit', year: '2-digit', hour: '2-digit', minute: '2-digit' }) }

const currentLaneIndex = computed(() => ticket.value ? lanes.value.findIndex(l => l.id === ticket.value!.laneId) : -1)
const availableLanes = computed(() => {
    const idx = currentLaneIndex.value
    if (idx < 0) return lanes.value
    const result = []
    if (idx < lanes.value.length - 1) result.push(lanes.value[idx + 1])
    result.push(lanes.value[idx])
    if (idx > 0) result.push(lanes.value[idx - 1])
    return result
})

const checklistVisible = computed(() => checklist.value.length > 0 || showChecklist.value || newChecklistTitle.value !== '')

const rightColRef = ref<HTMLElement | null>(null)
function closeAllEditors() { editingLane.value = false; editingPriority.value = false; editingAssignee.value = false; editingDueDate.value = false }
function handleClickOutside(e: MouseEvent) {
    if (showAddMenu.value && addMenuRef.value && !addMenuRef.value.contains(e.target as Node)) showAddMenu.value = false
    if (showTicketMenu.value) showTicketMenu.value = false
    if (rightColRef.value && !rightColRef.value.contains(e.target as Node)) { editingLane.value = false; editingPriority.value = false; editingAssignee.value = false; editingDueDate.value = false }
}
onMounted(() => { loadData(); document.addEventListener('click', handleClickOutside) })
onBeforeUnmount(() => document.removeEventListener('click', handleClickOutside))
watch(ticketNumber, loadData)
</script>

<template>
    <ViewContent>
        <Spinner v-if="loading" />
        <Alert v-else-if="error && !ticket" variant="error">{{ error }}</Alert>
        <template v-else-if="board && ticket">
            <div class="flex items-center gap-3 mb-6">
                <IconButton :icon="['fas', 'chevron-left']" label="Back" @click="router.push(api.backRoute.value)" />
                <span class="font-mono text-[var(--text-muted)]">{{ board.shortKey }}-{{ ticket.ticketNumber }}</span>
                <div class="ml-auto flex items-center gap-1">
                    <IconButton
                        :icon="['fas', isWatching ? 'eye-slash' : 'eye']"
                        :label="isWatching ? t('boards.unwatch') : t('boards.watch')"
                        :class="isWatching ? 'text-[var(--accent)]' : 'text-[var(--text-muted)]'"
                        @click="toggleWatch"
                    />
                    <div v-if="canEdit" class="relative">
                        <IconButton :icon="['fas', 'ellipsis']" label="Menu" class="text-[var(--text-muted)]" @click.stop="showTicketMenu = !showTicketMenu" />
                        <div v-if="showTicketMenu" class="absolute right-0 mt-1 w-40 rounded-theme border border-[var(--border)] bg-[var(--bg)] shadow-lg z-20">
                            <DeleteButton class="w-full text-left px-3 py-2 text-sm" @click="showDeleteModal = true; showTicketMenu = false">
                                {{ t('common.delete') }}
                            </DeleteButton>
                        </div>
                    </div>
                </div>
            </div>

            <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
                <div class="lg:col-span-2 space-y-4">
                    <TextInput v-if="editingTitle && canEdit" v-model="title" borderless class="text-lg font-semibold" @blur="editingTitle = false; saveTicket()" @keydown.enter="($event.target as HTMLInputElement).blur()" />
                    <div v-else class="text-lg font-semibold rounded-theme px-2 py-1" :class="canEdit ? 'cursor-pointer hover:bg-[var(--bg-accent)]' : ''" @click="canEdit && (editingTitle = true)">{{ title }}</div>

                    <!-- Add menu (+) -->
                    <div v-if="canEdit" ref="addMenuRef" class="relative inline-block">
                        <IconButton :icon="['fas', 'plus']" label="Add" class="text-(--text-muted)" @click.stop="showAddMenu = !showAddMenu" />
                        <div v-if="showAddMenu" class="absolute left-0 mt-1 w-48 rounded-theme border border-[var(--border)] bg-[var(--bg)] shadow-lg z-20">
                            <div v-if="!checklistVisible" class="px-3 py-2 text-sm cursor-pointer hover:bg-primary/5 flex items-center gap-2" @click="showChecklist = true; showAddMenu = false">
                                <font-awesome-icon :icon="['fas', 'list-check']" class="text-(--text-muted) w-4" />
                                {{ t('boards.checklist') }}
                            </div>
                            <div class="px-3 py-2 text-sm cursor-pointer hover:bg-primary/5 flex items-center gap-2" @click="showAddLink = true; showAddMenu = false">
                                <font-awesome-icon :icon="['fas', 'link']" class="text-(--text-muted) w-4" />
                                {{ t('boards.addLink') }}
                            </div>
                            <div class="px-3 py-2 text-sm cursor-pointer hover:bg-primary/5 flex items-center gap-2" @click="showAddWeblink = true; showAddMenu = false">
                                <font-awesome-icon :icon="['fas', 'globe']" class="text-(--text-muted) w-4" />
                                {{ t('boards.addWeblink') }}
                            </div>
                            <div class="px-3 py-2 text-sm cursor-pointer hover:bg-primary/5 flex items-center gap-2" @click="showAddMenu = false; fileInputRef?.inputRef?.click()">
                                <font-awesome-icon :icon="['fas', 'paperclip']" class="text-(--text-muted) w-4" />
                                {{ t('boards.addAttachment') }}
                            </div>
                            <div class="px-3 py-2 text-sm cursor-pointer hover:bg-primary/5 flex items-center gap-2" @click="showKbSearch = true; showAddMenu = false">
                                <font-awesome-icon :icon="['fas', 'book']" class="text-(--text-muted) w-4" />
                                {{ t('boards.addKbLink') }}
                            </div>
                        </div>
                        <div class="hidden"><FileUploadButton ref="fileInputRef" @select="handleFileUpload" /></div>
                    </div>

                    <div>
                        <FieldLabel class="mb-1">{{ t('boards.ticketDescription') }}</FieldLabel>
                        <div v-if="!editingDescription && description" class="prose prose-sm dark:prose-invert max-w-none rounded-theme p-2 min-h-[2rem]" :class="canEdit ? 'cursor-pointer hover:bg-[var(--bg-accent)]' : ''" @click="canEdit && (editingDescription = true)" v-html="renderMarkdown(description)" />
                        <div v-else-if="!editingDescription && canEdit" class="text-sm text-[var(--text-muted)] cursor-pointer rounded-theme p-2 hover:bg-[var(--bg-accent)] italic" @click="editingDescription = true">
                            {{ t('boards.clickToAddDescription') }}
                        </div>
                        <div v-else>
                            <MarkdownEditor v-model="description" :placeholder="t('boards.ticketDescription')" />
                            <div class="flex justify-end mt-2">
                                <PrimaryButton @click="editingDescription = false; saveTicket()">{{ t('common.save') }}</PrimaryButton>
                            </div>
                        </div>
                    </div>

                    <!-- Checklist -->
                    <TicketChecklist v-if="checklistVisible" :checklist="checklist" :new-title="newChecklistTitle" :readonly="!canEdit"
                        @update:new-title="newChecklistTitle = $event" @toggle="toggleChecklistItem"
                        @add="addChecklistItem" @remove="removeChecklistItem"
                        @remove-all="removeAllChecklistItems" @reorder="reorderChecklist" />

                    <!-- Links + Weblinks + Attachments -->
                    <TicketLinksSection
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
                        :show-add-link="showAddLink"
                        :show-add-weblink="showAddWeblink"
                        :readonly="!canEdit"
                        :partner-uid="api.partnerUid.value"
                        @update:show-add-link="showAddLink = $event"
                        @update:show-add-weblink="showAddWeblink = $event"
                        @reload="loadDetails"
                        @add-file="fileInputRef?.inputRef?.click()"
                    />

                    <!-- Activity -->
                    <!-- KB Links -->
                    <div v-if="kbLinks.length > 0 || showKbSearch">
                        <SubHeader class="text-sm mb-2">{{ t('boards.kbLinks') }}</SubHeader>
                        <div class="space-y-1 mb-2">
                            <div v-for="kl in kbLinks" :key="kl.id" class="flex items-center gap-2 text-sm group">
                                <font-awesome-icon :icon="['fas', 'book']" class="text-(--text-muted) text-xs shrink-0" />
                                <router-link :to="`/station/knowledge/${kl.kbFileId}`" class="text-primary hover:underline truncate flex-1">
                                    <span v-if="kl.folderPath && kl.folderPath !== '/'" class="text-(--text-muted) text-xs">{{ kl.folderPath }} /&nbsp;</span>{{ kl.title }}
                                </router-link>
                                <IconButton v-if="canEdit" :icon="['fas', 'xmark']" label="Remove" class="sm:opacity-0 sm:group-hover:opacity-100 text-xs" @click="removeKbLinkFn(kl.id)" />
                            </div>
                        </div>
                        <div v-if="showKbSearch" class="space-y-1">
                            <TextInput v-model="kbSearchQuery" :placeholder="t('boards.searchKb')" class="text-sm" @input="onKbSearch" />
                            <div v-if="kbSearchResults.length > 0" class="border border-(--border) rounded-theme divide-y divide-(--border)">
                                <div v-for="r in kbSearchResults" :key="r.id" class="px-3 py-1.5 text-sm cursor-pointer hover:bg-primary/5 flex items-center gap-2" @click="addKbLinkFn(r.id)">
                                    <font-awesome-icon :icon="['fas', 'book']" class="text-(--text-muted) text-xs shrink-0" />
                                    <div class="truncate"><span v-if="r.path && r.path !== '/'" class="text-(--text-muted) text-xs">{{ r.path }} /&nbsp;</span>{{ r.title }}</div>
                                </div>
                            </div>
                        </div>
                    </div>

                    <TicketActivity
                        :comments="comments"
                        :transitions="transitions"
                        :history="ticketHistory"
                        :lanes="lanes"
                        :labels="allLabels"
                        :members="members"
                        :readonly="!canEdit"
                        :federated="api.isFederated.value"
                        @create-comment="createComment"
                        @update-comment="updateComment"
                        @delete-comment="deleteCommentFn"
                    />
                </div>

                <!-- Right column -->
                <div ref="rightColRef" class="space-y-4">
                    <!-- Lane status -->
                    <div class="relative">
                        <div class="px-3 py-2 rounded-theme text-sm font-medium text-center cursor-pointer" :style="{ backgroundColor: lanes.find(l => l.id === ticket?.laneId)?.color ?? 'var(--primary)', color: contrastTextColor(lanes.find(l => l.id === ticket?.laneId)?.color ?? '#fd4f00') }" @click.stop="canEdit && (closeAllEditors(), editingLane = true)">{{ lanes.find(l => l.id === ticket?.laneId)?.name }}</div>
                        <div v-if="editingLane && canEdit" class="absolute z-20 mt-1 w-full rounded-theme border border-[var(--border)] bg-[var(--bg)] shadow-lg overflow-hidden">
                            <div v-for="lane in availableLanes.filter(l => l.id !== ticket?.laneId)" :key="lane.id" class="px-3 py-2 text-sm font-medium text-center cursor-pointer hover:opacity-90" :style="{ backgroundColor: lane.color ?? 'var(--primary)', color: contrastTextColor(lane.color ?? '#fd4f00') }" @click="moveTo(lane.id); editingLane = false">{{ lane.name }}</div>
                        </div>
                    </div>

                    <!-- Labels -->
                    <div v-if="allLabels.length > 0">
                        <FieldLabel class="mb-1">Labels</FieldLabel>
                        <LabelSelectInput v-if="canEdit" :labels="allLabels" :selected="ticketLabels" @toggle="toggleLabel" @create="createAndAddLabel" />
                        <div v-else class="flex flex-wrap gap-1">
                            <span v-for="label in ticketLabels" :key="label.id" class="text-xs px-2 py-0.5 rounded-full" :style="{ backgroundColor: label.color, color: contrastTextColor(label.color) }">{{ label.name }}</span>
                            <span v-if="ticketLabels.length === 0" class="text-sm text-(--text-muted)">—</span>
                        </div>
                    </div>

                    <!-- Priority -->
                    <div>
                        <FieldLabel class="mb-1">{{ t('boards.priority') }}</FieldLabel>
                        <IconSelectInput v-if="editingPriority && canEdit" v-model="priority" :options="priorityOptions" auto-open @update:model-value="editingPriority = false; saveTicket()" />
                        <div v-else class="flex items-center gap-2 rounded-theme px-2 py-1 text-sm" :class="canEdit ? 'cursor-pointer hover:bg-(--bg-accent)' : ''" @click.stop="canEdit && (closeAllEditors(), editingPriority = true)">
                            <font-awesome-icon :icon="priorityOptions.find(o => o.value === priority)?.icon ?? ['fas', 'equals']" :class="priorityOptions.find(o => o.value === priority)?.color" />
                            <span>{{ priorityOptions.find(o => o.value === priority)?.label }}</span>
                        </div>
                    </div>

                    <!-- Assignee -->
                    <div>
                        <FieldLabel class="mb-1">{{ t('boards.assignee') }}</FieldLabel>
                        <MemberSelectInput v-if="editingAssignee && canEdit" v-model="assignedMemberId" :members="members" :placeholder="t('boards.unassigned')" auto-open @change="editingAssignee = false; saveTicket()" />
                        <div v-else class="flex items-center gap-2 rounded-theme px-2 py-1 text-sm" :class="canEdit ? 'cursor-pointer hover:bg-(--bg-accent)' : ''" @click.stop="canEdit && (closeAllEditors(), editingAssignee = true)">
                            <span v-if="assignedMemberId" class="flex items-center gap-2">
                                <UserAvatar :member-id="Number(assignedMemberId)" size="sm" />
                                {{ members.find(m => m.id === Number(assignedMemberId))?.name }}
                            </span>
                            <span v-else class="text-(--text-muted) italic">{{ t('boards.unassigned') }}</span>
                        </div>
                    </div>

                    <!-- Due date -->
                    <div>
                        <FieldLabel class="mb-1">{{ t('boards.dueDate') }}</FieldLabel>
                        <DateInput v-if="editingDueDate && canEdit" v-model="dueDate" @change="editingDueDate = false; saveTicket()" @blur="editingDueDate = false" />
                        <div v-else class="rounded-theme px-2 py-1 text-sm" :class="canEdit ? 'cursor-pointer hover:bg-(--bg-accent)' : ''" @click.stop="canEdit && (closeAllEditors(), editingDueDate = true)">
                            <span v-if="dueDate">{{ new Date(dueDate).toLocaleDateString('de-DE') }}</span>
                            <span v-else class="text-(--text-muted) italic">—</span>
                        </div>
                    </div>

                    <!-- Custom fields -->
                    <div v-if="boardFields.length > 0" class="border-t border-[var(--border)] pt-4"></div>
                    <div v-for="field in boardFields" :key="field.id">
                        <FieldLabel class="mb-1">{{ field.name }}</FieldLabel>
                        <template v-if="canEdit">
                            <TextInput v-if="field.fieldType === 'STRING'" :model-value="(fieldValues[field.id] as string) ?? ''" @blur="(e: Event) => saveFieldValue(field.id, 'STRING', (e.target as HTMLInputElement).value || null)" />
                            <NumberInput v-else-if="field.fieldType === 'NUMBER'" :model-value="(fieldValues[field.id] as number) ?? 0" @blur="(e: Event) => saveFieldValue(field.id, 'NUMBER', Number((e.target as HTMLInputElement).value) || null)" />
                            <CheckboxInput v-else-if="field.fieldType === 'BOOLEAN'" :model-value="!!fieldValues[field.id]" @update:model-value="(v: boolean) => saveFieldValue(field.id, 'BOOLEAN', v)" />
                            <SelectInput v-else-if="field.fieldType === 'ENUM'" class="w-full" :model-value="(fieldValues[field.id] as string) ?? ''" @update:model-value="(v: any) => saveFieldValue(field.id, 'ENUM', v || null)">
                                <option value="">—</option>
                                <option v-for="opt in (field.config?.options ?? [])" :key="opt" :value="opt">{{ opt }}</option>
                            </SelectInput>
                            <DateInput v-else-if="field.fieldType === 'DATE'" :model-value="(fieldValues[field.id] as string) ?? ''" @change="(e: Event) => saveFieldValue(field.id, 'DATE', (e.target as HTMLInputElement).value || null)" />
                            <MemberSelectInput v-else-if="field.fieldType === 'LANE_ASSIGNEE'" :model-value="String(fieldValues[field.id] ?? '')" :members="members" :placeholder="t('boards.unassigned')" @change="saveFieldValue(field.id, 'LANE_ASSIGNEE', Number(fieldValues[field.id]) || null)" @update:model-value="(v: any) => { fieldValues[field.id] = v ? Number(v) : null; saveFieldValue(field.id, 'LANE_ASSIGNEE', v ? Number(v) : null) }" />
                        </template>
                        <div v-else class="text-sm px-2 py-1">{{ fieldValues[field.id] ?? '—' }}</div>
                    </div>

                    <div class="text-xs text-[var(--text-muted)] space-y-1 pt-4 border-t border-[var(--border)]">
                        <p v-if="ticket.createdAt">Erstellt: {{ formatDate(ticket.createdAt) }}</p>
                        <p v-if="ticket.updatedAt">Geändert: {{ formatDate(ticket.updatedAt) }}</p>
                    </div>

                    <Alert v-if="error" variant="error">{{ error }}</Alert>
                </div>
            </div>

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
