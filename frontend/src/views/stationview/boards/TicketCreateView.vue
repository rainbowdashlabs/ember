/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import MemberSelectInput from '@/components/input/select/MemberSelectInput.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import MarkdownEditor from '@/components/input/MarkdownEditor.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import LabelSelectInput from '@/components/input/select/LabelSelectInput.vue'
import { boards, stationMembers } from '@/api'
import type { Board, BoardLane, BoardLabel } from '@/api/boards'
import { TicketPriority } from '@/api/boards'
import type { TicketPriorityName, LinkTypeName } from '@/api/boards'
import { LinkType } from '@/api/boards'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const boardKey = computed(() => route.params.boardKey as string)

const board = ref<Board | null>(null)
const lanes = ref<BoardLane[]>([])
const members = ref<{ id: number; name: string }[]>([])
const allLabels = ref<BoardLabel[]>([])
const allTickets = ref<{ id: number; ticketNumber: number; title: string }[]>([])
const loading = ref(true)
const submitting = ref(false)
const error = ref('')

// -- Form state --
const title = ref('')
const description = ref('')
const laneId = ref('')
const priority = ref<TicketPriorityName>(TicketPriority.MEDIUM)
const assignee = ref('')
const dueDate = ref('')

// Checklist (local, created after ticket)
interface DraftChecklistItem { key: number; title: string; checked: boolean }
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

// Labels (selected ids, attached after ticket)
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
    } catch { /* ignore */ }
}

// Weblinks (local, created after ticket)
interface DraftWeblink { key: number; url: string; title: string }
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

// Links (local, created after ticket)
interface DraftLink { key: number; linkedTicketId: number; linkType: LinkTypeName }
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

function linkedTicketLabel(linkedTicketId: number): string {
    const tk = allTickets.value.find(t => t.id === linkedTicketId)
    return tk ? `${board.value?.shortKey}-${tk.ticketNumber} ${tk.title}` : String(linkedTicketId)
}

// Lane options: backlog + first visible lane
const createLaneOptions = computed(() => {
    if (!board.value) return []
    const options: BoardLane[] = []
    const backlog = board.value.backlogLaneId ? lanes.value.find(l => l.id === board.value!.backlogLaneId) : null
    if (backlog) options.push(backlog)
    const firstVisible = lanes.value.find(l => !board.value?.backlogLaneId || l.id !== board.value.backlogLaneId)
    if (firstVisible) options.push(firstVisible)
    return options
})

async function loadData() {
    loading.value = true
    try {
        const [b, l, m, lb, tks] = await Promise.all([
            boards.getBoard(boardKey.value),
            boards.getLanes(boardKey.value),
            stationMembers.listCompletions(),
            boards.getLabels(boardKey.value),
            boards.listTickets(boardKey.value),
        ])
        board.value = b
        lanes.value = l
        members.value = m
        allLabels.value = lb
        allTickets.value = tks.map(tk => ({ id: tk.id, ticketNumber: tk.ticketNumber, title: tk.title }))

        // Apply query params (carried over from quick-create modal)
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
    } catch {
        error.value = t('common.error')
    } finally {
        loading.value = false
    }
}

async function handleSubmit() {
    if (!title.value.trim()) {
        error.value = t('common.requiredField')
        return
    }
    submitting.value = true
    error.value = ''
    try {
        const created = await boards.createTicket(boardKey.value, {
            laneId: Number(laneId.value),
            title: title.value.trim(),
            description: description.value.trim() || undefined,
            priority: priority.value,
            assignedMemberId: assignee.value ? Number(assignee.value) : undefined,
            dueDate: dueDate.value || undefined,
        })
        const ticketNumber = created.ticketNumber

        // Create all extras in parallel where possible
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
    } catch {
        error.value = t('common.error')
    } finally {
        submitting.value = false
    }
}

onMounted(loadData)
</script>

<template>
    <ViewContent>
        <Spinner v-if="loading" />
        <Alert v-else-if="error && !board" variant="error">{{ error }}</Alert>
        <template v-else-if="board">
            <div class="flex items-center gap-3 mb-6">
                <IconButton :icon="['fas', 'chevron-left']" label="Back" @click="router.push(`/station/boards/${board.shortKey}`)" />
                <SectionHeader>{{ t('boards.createTicket') }}</SectionHeader>
                <span class="text-xs font-mono text-(--text-muted) bg-(--bg-accent) px-1.5 py-0.5 rounded">{{ board.shortKey }}</span>
            </div>

            <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
                <!-- Main column -->
                <div class="lg:col-span-2 space-y-4">
                    <div>
                        <FieldLabel class="mb-1">{{ t('boards.ticketTitle') }} *</FieldLabel>
                        <TextInput v-model="title" />
                    </div>

                    <div>
                        <FieldLabel class="mb-1">{{ t('boards.ticketDescription') }}</FieldLabel>
                        <MarkdownEditor v-model="description" :placeholder="t('boards.ticketDescription')" />
                    </div>

                    <!-- Checklist -->
                    <NeutralContainer>
                        <SubHeader class="mb-2">{{ t('boards.checklist') }}</SubHeader>
                        <div v-for="item in checklistItems" :key="item.key" class="flex items-center gap-2 py-0.5">
                            <CheckboxInput :model-value="item.checked" @update:model-value="toggleChecklistItem(item.key)" />
                            <span class="flex-1 text-sm" :class="{ 'line-through text-(--text-muted)': item.checked }">{{ item.title }}</span>
                            <IconButton :icon="['fas', 'xmark']" label="Remove" class="text-xs" @click="removeChecklistItem(item.key)" />
                        </div>
                        <div class="flex gap-2 mt-2 items-center">
                            <TextInput v-model="newChecklistTitle" :placeholder="t('boards.addChecklistItem')" class="flex-1 text-sm" @keydown.enter="addChecklistItem" />
                            <IconButton :icon="['fas', 'plus']" :label="t('common.add')" class="text-(--text-muted)" @click="addChecklistItem" />
                        </div>
                    </NeutralContainer>

                    <!-- Weblinks -->
                    <NeutralContainer>
                        <SubHeader class="mb-2">{{ t('boards.weblinks') }}</SubHeader>
                        <div v-for="wl in weblinks" :key="wl.key" class="flex items-center gap-2 py-0.5 group">
                            <font-awesome-icon :icon="['fas', 'globe']" class="text-(--text-muted) text-xs shrink-0" />
                            <span class="text-sm truncate flex-1">{{ wl.title || wl.url }}</span>
                            <IconButton :icon="['fas', 'xmark']" label="Remove" class="text-xs sm:opacity-0 sm:group-hover:opacity-100" @click="removeWeblink(wl.key)" />
                        </div>
                        <div class="flex gap-2 mt-2 items-center">
                            <TextInput v-model="newWeblinkUrl" placeholder="https://..." class="flex-1 text-sm" />
                            <TextInput v-model="newWeblinkTitle" :placeholder="t('boards.weblinkTitle')" class="flex-1 text-sm" @keydown.enter="addWeblink" />
                            <IconButton :icon="['fas', 'plus']" :label="t('common.add')" class="text-(--text-muted)" @click="addWeblink" />
                        </div>
                    </NeutralContainer>

                    <!-- Ticket links -->
                    <NeutralContainer>
                        <SubHeader class="mb-2">{{ t('boards.links') }}</SubHeader>
                        <div v-for="link in ticketLinks" :key="link.key" class="flex items-center gap-2 py-0.5 group">
                            <font-awesome-icon :icon="['fas', 'link']" class="text-(--text-muted) text-xs shrink-0" />
                            <span class="text-xs text-(--text-muted)">{{ t('boards.link' + link.linkType.charAt(0) + link.linkType.slice(1).toLowerCase().replace(/_./g, m => m[1].toUpperCase())) }}</span>
                            <span class="text-sm truncate flex-1">{{ linkedTicketLabel(link.linkedTicketId) }}</span>
                            <IconButton :icon="['fas', 'xmark']" label="Remove" class="text-xs sm:opacity-0 sm:group-hover:opacity-100" @click="removeLink(link.key)" />
                        </div>
                        <div class="flex gap-2 mt-2 items-center flex-wrap">
                            <SelectInput v-model="newLinkType" class="w-40">
                                <option :value="LinkType.RELATES_TO">{{ t('boards.linkRelatesTo') }}</option>
                                <option :value="LinkType.BLOCKS">{{ t('boards.linkBlocks') }}</option>
                                <option :value="LinkType.BLOCKED_BY">{{ t('boards.linkBlockedBy') }}</option>
                                <option :value="LinkType.CAUSES">{{ t('boards.linkCauses') }}</option>
                                <option :value="LinkType.CAUSED_BY">{{ t('boards.linkCausedBy') }}</option>
                            </SelectInput>
                            <SelectInput v-model="newLinkTicketId" class="flex-1 min-w-0">
                                <option value="">{{ t('boards.linkedTickets') }}...</option>
                                <option v-for="tk in allTickets" :key="tk.id" :value="tk.id">{{ board.shortKey }}-{{ tk.ticketNumber }} {{ tk.title }}</option>
                            </SelectInput>
                            <IconButton :icon="['fas', 'plus']" :label="t('common.add')" class="text-(--text-muted)" @click="addLink" />
                        </div>
                    </NeutralContainer>
                </div>

                <!-- Right column -->
                <div class="space-y-4">
                    <div>
                        <FieldLabel class="mb-1">{{ t('boards.lanes') }}</FieldLabel>
                        <SelectInput v-model="laneId" class="w-full">
                            <option v-for="lane in createLaneOptions" :key="lane.id" :value="lane.id">{{ lane.name }}</option>
                        </SelectInput>
                    </div>

                    <div>
                        <FieldLabel class="mb-1">{{ t('boards.priority') }}</FieldLabel>
                        <SelectInput v-model="priority" class="w-full">
                            <option :value="TicketPriority.LOWEST">{{ t('boards.priorityLowest') }}</option>
                            <option :value="TicketPriority.LOW">{{ t('boards.priorityLow') }}</option>
                            <option :value="TicketPriority.MEDIUM">{{ t('boards.priorityMedium') }}</option>
                            <option :value="TicketPriority.HIGH">{{ t('boards.priorityHigh') }}</option>
                            <option :value="TicketPriority.HIGHEST">{{ t('boards.priorityHighest') }}</option>
                        </SelectInput>
                    </div>

                    <div>
                        <FieldLabel class="mb-1">{{ t('boards.assignee') }}</FieldLabel>
                        <MemberSelectInput v-model="assignee" :members="members" :placeholder="t('boards.unassigned')" />
                    </div>

                    <div>
                        <FieldLabel class="mb-1">{{ t('boards.dueDate') }}</FieldLabel>
                        <DateInput v-model="dueDate" />
                    </div>

                    <!-- Labels -->
                    <div v-if="allLabels.length > 0">
                        <FieldLabel class="mb-1">Labels</FieldLabel>
                        <LabelSelectInput :labels="allLabels" :selected="selectedLabels" @toggle="toggleLabel" @create="createAndSelectLabel" />
                    </div>

                    <Alert v-if="error" variant="error">{{ error }}</Alert>

                    <div class="flex gap-2">
                        <SecondaryButton class="flex-1" @click="router.push(`/station/boards/${board.shortKey}`)">{{ t('common.cancel') }}</SecondaryButton>
                        <PrimaryButton class="flex-1" :disabled="submitting" @click="handleSubmit">
                            <Spinner v-if="submitting" size="sm" class="mr-1" />
                            {{ t('common.create') }}
                        </PrimaryButton>
                    </div>
                </div>
            </div>
        </template>
    </ViewContent>
</template>
