/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import IconButton from '@/components/button/IconButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import TicketCreateMainColumn from './ticketcreateview/TicketCreateMainColumn.vue'
import TicketCreateRightColumn from './ticketcreateview/TicketCreateRightColumn.vue'
import type { DraftChecklistItem } from './ticketcreateview/TicketChecklistDraft.vue'
import type { DraftWeblink } from './ticketcreateview/TicketWeblinksDraft.vue'
import type { DraftLink, TicketOption } from './ticketcreateview/TicketLinksDraft.vue'
import { boards } from '@/api'
import type { MemberCompletion } from '@/api/stationMembers'
import {LinkType, TicketPriority, type Board, type BoardLabel, type BoardLane, type LinkTypeName, type TicketPriorityName} from '@/api/boards'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import { useAsyncAction } from '@/composables/useAsyncAction'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const boardKey = computed(() => route.params.boardKey as string)

const board = ref<Board | null>(null)
const lanes = ref<BoardLane[]>([])
const assignableMembers = ref<MemberCompletion[]>([])
const allLabels = ref<BoardLabel[]>([])

const allTickets = ref<TicketOption[]>([])

const title = ref('')
const description = ref('')
const laneId = ref('')
const priority = ref<TicketPriorityName>(TicketPriority.MEDIUM)
const assignee = ref('')
const dueDate = ref('')

let nextKey = 0
const checklistItems = ref<DraftChecklistItem[]>([])
const newChecklistTitle = ref('')

function addChecklistItem() {
    if (!newChecklistTitle.value.trim()) return
    checklistItems.value.push({ key: nextKey++, title: newChecklistTitle.value.trim(), checked: false })
    newChecklistTitle.value = ''
}

function removeChecklistItem(key: number) {
    checklistItems.value = checklistItems.value.filter(i => i.key !== key)
}

function toggleChecklistItem(key: number) {
    const item = checklistItems.value.find(i => i.key === key)
    if (item) item.checked = !item.checked
}

const selectedLabelIds = ref<Set<number>>(new Set())
const selectedLabels = computed(() => allLabels.value.filter(l => selectedLabelIds.value.has(l.id)))

function toggleLabel(labelId: number) {
    const next = new Set(selectedLabelIds.value)
    if (next.has(labelId)) next.delete(labelId); else next.add(labelId)
    selectedLabelIds.value = next
}

async function createAndSelectLabel(name: string) {
    try {
        const label = await boards.createLabel(boardKey.value, { name })
        allLabels.value = await boards.getLabels(boardKey.value)
        const next = new Set(selectedLabelIds.value)
        next.add(label.id)
        selectedLabelIds.value = next
    } catch { void 0 }
}

const weblinks = ref<DraftWeblink[]>([])
const newWeblinkUrl = ref('')
const newWeblinkTitle = ref('')

function addWeblink() {
    if (!newWeblinkUrl.value.trim()) return
    weblinks.value.push({ key: nextKey++, url: newWeblinkUrl.value.trim(), title: newWeblinkTitle.value.trim() })
    newWeblinkUrl.value = ''
    newWeblinkTitle.value = ''
}

function removeWeblink(key: number) {
    weblinks.value = weblinks.value.filter(w => w.key !== key)
}

const ticketLinks = ref<DraftLink[]>([])
const newLinkTicketId = ref('')
const newLinkType = ref<LinkTypeName>(LinkType.RELATES_TO)

function addLink() {
    if (!newLinkTicketId.value) return
    const linkedId = Number(newLinkTicketId.value)
    if (ticketLinks.value.some(l => l.linkedTicketId === linkedId)) return
    ticketLinks.value.push({ key: nextKey++, linkedTicketId: linkedId, linkType: newLinkType.value })
    newLinkTicketId.value = ''
    newLinkType.value = LinkType.RELATES_TO
}

function removeLink(key: number) {
    ticketLinks.value = ticketLinks.value.filter(l => l.key !== key)
}

const createLaneOptions = computed(() => {
    if (!board.value) return []
    const options: BoardLane[] = []
    const backlog = board.value.backlogLaneId ? lanes.value.find(l => l.id === board.value!.backlogLaneId) : null
    if (backlog) options.push(backlog)
    const firstVisible = lanes.value.find(l => !board.value?.backlogLaneId || l.id !== board.value.backlogLaneId)
    if (firstVisible) options.push(firstVisible)
    return options
})

const {loading, error} = useAsyncLoader(async () => {
    const [b, l, m, lb, tks] = await Promise.all([
        boards.getBoard(boardKey.value),
        boards.getLanes(boardKey.value),
        boards.getAssignableMembers(boardKey.value),
        boards.getLabels(boardKey.value),
        boards.listTickets(boardKey.value),
    ])
    board.value = b
    lanes.value = l
    assignableMembers.value = m
    allLabels.value = lb
    allTickets.value = tks.map(tk => ({ id: tk.id, ticketNumber: tk.ticketNumber, title: tk.title }))

    const q = route.query
    if (q.title) title.value = String(q.title)
    if (q.description) description.value = String(q.description)
    if (q.priority) priority.value = String(q.priority) as TicketPriorityName
    if (q.assignee) assignee.value = String(q.assignee)
    if (q.dueDate) dueDate.value = String(q.dueDate)

    if (q.laneId) {
        laneId.value = String(q.laneId)
    } else {
        const firstAllowed = b.backlogLaneId ?? l.find(la => la.id !== b.backlogLaneId)?.id
        if (firstAllowed) laneId.value = String(firstAllowed)
    }
})

const {running: submitting, error: submitError, run: runSubmit} = useAsyncAction(async () => {
    const created = await boards.createTicket(boardKey.value, {
        laneId: Number(laneId.value),
        title: title.value.trim(),
        description: description.value.trim() || undefined,
        priority: priority.value,
        assignedMemberId: assignee.value ? Number(assignee.value) : undefined,
        dueDate: dueDate.value || undefined,
    })
    const ticketNumber = created.ticketNumber

    const ops: Promise<unknown>[] = []
    for (const item of checklistItems.value) {
        ops.push(boards.addChecklistItem(boardKey.value, ticketNumber, { title: item.title }))
    }
    for (const labelId of selectedLabelIds.value) {
        ops.push(boards.addTicketLabel(boardKey.value, ticketNumber, labelId))
    }
    for (const wl of weblinks.value) {
        ops.push(boards.addWeblink(boardKey.value, ticketNumber, { url: wl.url, title: wl.title || undefined }))
    }
    for (const link of ticketLinks.value) {
        ops.push(boards.createLink(boardKey.value, ticketNumber, { linkedTicketId: link.linkedTicketId, linkType: link.linkType }))
    }

    await Promise.all(ops)
    await router.push(`/station/boards/${boardKey.value}/tickets/${ticketNumber}`)
}, {formatError: () => t('common.error')})

function handleSubmit() {
    if (!title.value.trim()) {
        error.value = t('common.requiredField')
        return
    }
    error.value = ''
    void runSubmit()
}

function goBack() {
    if (board.value) router.push(`/station/boards/${board.value.shortKey}`)
}

</script>

<template>
    <ViewContent
        :title="t('pages.ticket-create.title')"
        :subtitle="t('pages.ticket-create.subtitle')"
    >
        <Spinner v-if="loading" />
        <Alert v-else-if="error && !board" variant="error">{{ error }}</Alert>
        <template v-else-if="board">
            <div class="flex items-center gap-3 mb-6">
                <IconButton :icon="['fas', 'chevron-left']" label="Back" @click="goBack" />
                <SectionHeader>{{ t('boards.createTicket') }}</SectionHeader>
                <span class="text-xs font-mono text-(--text-muted) bg-(--bg-accent) px-1.5 py-0.5 rounded">{{ board.shortKey }}</span>
            </div>
            <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
                <TicketCreateMainColumn
                    v-model:title="title"
                    v-model:description="description"
                    :checklist-items="checklistItems"
                    v-model:new-checklist-title="newChecklistTitle"
                    :weblinks="weblinks"
                    v-model:new-weblink-url="newWeblinkUrl"
                    v-model:new-weblink-title="newWeblinkTitle"
                    :ticket-links="ticketLinks"
                    v-model:new-link-ticket-id="newLinkTicketId"
                    v-model:new-link-type="newLinkType"
                    :all-tickets="allTickets"
                    :short-key="board.shortKey"
                    @add-checklist="addChecklistItem"
                    @remove-checklist="removeChecklistItem"
                    @toggle-checklist="toggleChecklistItem"
                    @add-weblink="addWeblink"
                    @remove-weblink="removeWeblink"
                    @add-link="addLink"
                    @remove-link="removeLink"
                />
                <TicketCreateRightColumn
                    v-model:lane-id="laneId"
                    v-model:priority="priority"
                    v-model:assignee="assignee"
                    v-model:due-date="dueDate"
                    :create-lane-options="createLaneOptions"
                    :assignable-members="assignableMembers"
                    :all-labels="allLabels"
                    :selected-labels="selectedLabels"
                    :error="error || submitError"
                    :submitting="submitting"
                    :cancel-to="`/station/boards/${board.shortKey}`"
                    @toggle-label="toggleLabel"
                    @create-label="createAndSelectLabel"
                    @cancel="goBack"
                    @submit="handleSubmit"
                />
            </div>
        </template>
    </ViewContent>
</template>
