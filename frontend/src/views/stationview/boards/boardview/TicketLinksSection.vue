/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { ref, computed, watch, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import UserAvatar from '@/components/avatar/UserAvatar.vue'
import { boards } from '@/api'
import { LinkType } from '@/api/boards'
import type { BoardTicketLink, BoardTicket, BoardLane, BoardWeblink, BoardTicketAttachment, LinkTypeName } from '@/api/boards'
import type { MemberCompletion } from '@/api/stationMembers'
import { useBoardApi } from '@/composables/useBoardApi'
import { useAuthImages } from '@/composables/useAuthImage'
import { downloadAuthed } from '@/util/downloadAuthed'

const props = defineProps<{
    boardKey: string
    ticketNumber: number
    shortKey: string
    links: BoardTicketLink[]
    weblinks: BoardWeblink[]
    attachments: BoardTicketAttachment[]
    allTickets: BoardTicket[]
    lanes: BoardLane[]
    members: MemberCompletion[]
    priorityOptions: { value: string; label: string; icon: string[]; color?: string }[]
    readonly?: boolean
    partnerUid?: string
}>()

const showAddLink = defineModel<boolean>('showAddLink', {required: true})
const showAddWeblink = defineModel<boolean>('showAddWeblink', {required: true})

const emit = defineEmits<{
    reload: []
    addFile: []
}>()
const { t } = useI18n()

function ticketPath(ticketNumber: number): string {
    if (props.partnerUid) {
        return `/station/federation/boards/${props.partnerUid}/${props.boardKey}/tickets/${ticketNumber}`
    }
    return `/station/boards/${props.boardKey}/tickets/${ticketNumber}`
}

const api = useBoardApi()

const linkSearchQuery = ref('')
const linkType = ref<LinkTypeName>(LinkType.RELATES_TO)
const selectedIndex = ref(0)
const newWeblinkUrl = ref('')
const newWeblinkTitle = ref('')

const linkSearchResults = computed(() => {
    if (!linkSearchQuery.value.trim()) return []
    const q = linkSearchQuery.value.toLowerCase()
    const linkedIds = new Set(props.links.map(l => l.linkedTicketId === props.ticketNumber ? l.ticketId : l.linkedTicketId))
    return props.allTickets
        .filter(t => t.ticketNumber !== props.ticketNumber && !linkedIds.has(t.id))
        .filter(t => t.title.toLowerCase().includes(q) || `${props.shortKey}-${t.ticketNumber}`.toLowerCase().includes(q))
        .slice(0, 5)
})

watch(linkSearchQuery, () => { selectedIndex.value = 0 })

function onSearchKeydown(e: KeyboardEvent) {
    if (e.key === 'ArrowDown') {
        e.preventDefault()
        selectedIndex.value = Math.min(selectedIndex.value + 1, linkSearchResults.value.length - 1)
    } else if (e.key === 'ArrowUp') {
        e.preventDefault()
        selectedIndex.value = Math.max(selectedIndex.value - 1, 0)
    } else if (e.key === 'Enter') {
        e.preventDefault()
        const selected = linkSearchResults.value[selectedIndex.value]
        if (selected) addLink(selected.id, selected.ticketNumber)
    }
}

const groupedLinks = computed(() => {
    const groups: Record<string, { link: BoardTicketLink; linkedTicket: BoardTicket | undefined }[]> = {}
    for (const link of props.links) {
        const linkedId = link.linkedTicketId === props.ticketNumber ? link.ticketId : link.linkedTicketId
        const linkedTicket = props.allTickets.find(t => t.id === linkedId)
        const group = groups[link.linkType] ?? (groups[link.linkType] = [])
        group.push({ link, linkedTicket })
    }
    return groups
})

function linkTypeLabel(type: string): string {
    const labels: Record<string, string> = {
        RELATES_TO: t('boards.linkRelatesTo'), BLOCKS: t('boards.linkBlocks'),
        BLOCKED_BY: t('boards.linkBlockedBy'), CAUSES: t('boards.linkCauses'),
        CAUSED_BY: t('boards.linkCausedBy'),
    }
    return labels[type] ?? type
}
function laneName(laneId: number) { return props.lanes.find(l => l.id === laneId)?.name ?? '' }


async function addLink(linkedTicketId: number, linkedTicketNumber: number) {
    await api.createLink(linkedTicketId, linkedTicketNumber, linkType.value)
    showAddLink.value = false; linkSearchQuery.value = ''; emit('reload')
}
async function removeLink(linkedTicketId: number, linkedTicketNumber: number) { await api.deleteLink(linkedTicketId, linkedTicketNumber); emit('reload') }
async function handleAddWeblink() {
    if (!newWeblinkUrl.value.trim()) return
    await boards.addWeblink(props.boardKey, props.ticketNumber, { url: newWeblinkUrl.value.trim(), title: newWeblinkTitle.value.trim() || undefined })
    newWeblinkUrl.value = ''; newWeblinkTitle.value = ''; showAddWeblink.value = false; emit('reload')
}
async function removeWeblink(id: number) { await boards.deleteWeblink(props.boardKey, props.ticketNumber, id); emit('reload') }
async function removeAttachment(id: number) { await boards.deleteAttachment(props.boardKey, props.ticketNumber, id); emit('reload') }

const previewOverlayRef = ref<HTMLElement | null>(null)
const previewUrl = ref<string | null>(null)
const previewName = ref('')
const previewCsv = ref<string[][] | null>(null)
const showPreview = ref(false)
const previewIndex = ref(0)

function isImage(ct: string) { return ct.startsWith('image/') }
function isPdf(ct: string) { return ct === 'application/pdf' }
function isCsv(name: string) { return name.toLowerCase().endsWith('.csv') }
function canPreview(att: BoardTicketAttachment) { return isImage(att.contentType) || isPdf(att.contentType) || isCsv(att.originalName) }
function fileIcon(att: BoardTicketAttachment): string[] {
    if (isImage(att.contentType)) return ['fas', 'image']
    if (isPdf(att.contentType)) return ['fas', 'file-pdf']
    if (isCsv(att.originalName)) return ['fas', 'file-lines']
    if (att.contentType.startsWith('text/')) return ['fas', 'file-lines']
    return ['fas', 'file']
}

const {srcFor, load: loadAttachmentBlob} = useAuthImages<number>()

function attachmentUrl(attachmentId: number): string {
    return `/boards/${props.boardKey}/tickets/${props.ticketNumber}/attachments/${attachmentId}/download`
}

async function loadThumbnails() {
    for (const att of props.attachments) {
        if (isImage(att.contentType) && !srcFor(att.id)) {
            await loadAttachmentBlob(att.id, attachmentUrl(att.id))
        }
    }
}
watch(() => props.attachments, loadThumbnails, { immediate: true })

async function openPreview(att: BoardTicketAttachment) {
    previewIndex.value = props.attachments.findIndex(a => a.id === att.id)
    await loadPreview(att)
}

watch(showPreview, (v) => { if (v) nextTick(() => previewOverlayRef.value?.focus()) })

async function loadPreview(att: BoardTicketAttachment) {
    previewName.value = att.originalName; previewCsv.value = null; previewUrl.value = null
    showPreview.value = true
    if (isImage(att.contentType) || isPdf(att.contentType)) {
        if (!srcFor(att.id)) await loadAttachmentBlob(att.id, attachmentUrl(att.id))
        previewUrl.value = srcFor(att.id)
    } else if (isCsv(att.originalName)) { previewCsv.value = parseCsv(await boards.getAttachmentText(props.boardKey, props.ticketNumber, att.id)) }
}

function previewAt(index: number) { const att = props.attachments[index]; if (att) { previewIndex.value = index; loadPreview(att) } }
function previewPrev() { if (previewIndex.value > 0) previewAt(previewIndex.value - 1) }
function previewNext() { if (previewIndex.value < props.attachments.length - 1) previewAt(previewIndex.value + 1) }
async function downloadCurrent() { const att = props.attachments[previewIndex.value]; if (att) await handleDownload(att) }

function parseCsv(text: string): string[][] {
    return text.trim().split('\n').map(line => {
        const cols: string[] = []; let cur = ''; let inQuote = false
        for (const ch of line) { if (ch === '"') { inQuote = !inQuote } else if (ch === ',' && !inQuote) { cols.push(cur); cur = '' } else if (ch === ';' && !inQuote) { cols.push(cur); cur = '' } else { cur += ch } }
        cols.push(cur); return cols
    })
}

async function handleDownload(att: BoardTicketAttachment) {
    await downloadAuthed(attachmentUrl(att.id), att.originalName)
}

function formatFileSize(bytes: number): string {
    if (bytes < 1024) return bytes + ' B'
    if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB'
    return (bytes / 1048576).toFixed(1) + ' MB'
}

const hasAnyContent = computed(() => props.links.length > 0 || props.weblinks.length > 0 || props.attachments.length > 0 || showAddLink.value || showAddWeblink.value)
</script>

<template>
    <div v-if="hasAnyContent" class="space-y-4">
        <!-- Linked tickets -->
        <div v-if="links.length > 0 || showAddLink">
            <div class="flex items-center justify-between mb-2">
                <SubHeader class="text-sm">{{ t('boards.linkedTickets') }}</SubHeader>
                <IconButton v-if="!showAddLink && !readonly" :icon="['fas', 'plus']" :label="t('boards.addLink')" class="text-(--text-muted) text-xs" @click="showAddLink = true" />
            </div>
            <div v-for="(items, type) in groupedLinks" :key="type" class="mb-2">
                <div class="text-xs font-semibold text-(--text-muted) uppercase tracking-wide mb-1">{{ linkTypeLabel(type as string) }}</div>
                <div class="divide-y divide-(--border) border border-(--border) rounded-theme overflow-hidden">
                    <div v-for="{ link, linkedTicket } in items" :key="`${link.ticketId}-${link.linkedTicketId}`" class="flex items-center gap-3 px-3 py-2 text-sm group hover:bg-primary/5">
                        <router-link :to="ticketPath(linkedTicket?.ticketNumber ?? 0)" class="flex items-center gap-2 flex-1 min-w-0">
                            <span class="font-mono text-(--text-muted) shrink-0">{{ shortKey }}-{{ linkedTicket?.ticketNumber }}</span>
                            <span class="truncate">{{ linkedTicket?.title ?? '?' }}</span>
                        </router-link>
                        <div class="flex items-center gap-2 shrink-0">
                            <span v-if="linkedTicket" class="text-xs text-(--text-muted)">{{ laneName(linkedTicket.laneId) }}</span>
                            <UserAvatar v-if="linkedTicket?.assignee" :identity="linkedTicket.assignee" :name="members.find(m => m.memberUid === linkedTicket.assignee?.memberUid)?.name" size="sm" />
                            <font-awesome-icon v-if="linkedTicket" :icon="priorityOptions.find(o => o.value === linkedTicket.priority)?.icon ?? ['fas', 'equals']" :class="priorityOptions.find(o => o.value === linkedTicket.priority)?.color" class="text-xs" />
                            <IconButton v-if="!readonly" :icon="['fas', 'xmark']" label="Remove" class="opacity-0 group-hover:opacity-100 text-xs" @click="removeLink(linkedTicket?.id ?? 0, linkedTicket?.ticketNumber ?? 0)" />
                        </div>
                    </div>
                </div>
            </div>
            <div v-if="showAddLink" class="space-y-2">
                <div class="flex gap-2 items-center">
                    <TextInput v-model="linkSearchQuery" :placeholder="t('boards.searchTickets')" class="flex-1 min-w-0" @keydown="onSearchKeydown" />
                    <div class="w-32 shrink-0">
                        <SelectInput v-model="linkType" class="text-xs">
                            <option :value="LinkType.RELATES_TO">{{ t('boards.linkRelatesTo') }}</option>
                            <option :value="LinkType.BLOCKS">{{ t('boards.linkBlocks') }}</option>
                            <option :value="LinkType.BLOCKED_BY">{{ t('boards.linkBlockedBy') }}</option>
                            <option :value="LinkType.CAUSES">{{ t('boards.linkCauses') }}</option>
                            <option :value="LinkType.CAUSED_BY">{{ t('boards.linkCausedBy') }}</option>
                        </SelectInput>
                    </div>
                    <IconButton :icon="['fas', 'xmark']" label="Cancel" class="text-(--text-muted)" @click="showAddLink = false; linkSearchQuery = ''" />
                </div>
                <div v-if="linkSearchResults.length > 0" class="border border-(--border) rounded-theme divide-y divide-(--border)">
                    <div v-for="(result, i) in linkSearchResults" :key="result.id" class="px-3 py-1.5 text-sm cursor-pointer flex items-center gap-2" :class="i === selectedIndex ? 'bg-primary/10' : 'hover:bg-primary/5'" @click="addLink(result.id, result.ticketNumber)" @mouseenter="selectedIndex = i">
                        <span class="font-mono text-(--text-muted)">{{ shortKey }}-{{ result.ticketNumber }}</span>
                        <span class="truncate">{{ result.title }}</span>
                    </div>
                </div>
            </div>
        </div>

        <!-- Weblinks -->
        <div v-if="weblinks.length > 0 || showAddWeblink">
            <SubHeader class="text-sm mb-2">{{ t('boards.weblinks') }}</SubHeader>
            <div class="space-y-1">
                <div v-for="wl in weblinks" :key="wl.id" class="flex items-center gap-2 text-sm group">
                    <font-awesome-icon :icon="['fas', 'globe']" class="text-(--text-muted) text-xs" />
                    <a :href="wl.url" target="_blank" rel="noopener" class="text-(--accent) hover:underline truncate flex-1">{{ wl.title || wl.url }}</a>
                    <IconButton v-if="!readonly" :icon="['fas', 'xmark']" label="Remove" class="opacity-0 group-hover:opacity-100 text-xs" @click="removeWeblink(wl.id)" />
                </div>
            </div>
            <div v-if="showAddWeblink" class="flex gap-2 mt-2">
                <TextInput v-model="newWeblinkUrl" placeholder="https://..." class="flex-1" />
                <TextInput v-model="newWeblinkTitle" :placeholder="t('boards.weblinkTitle')" class="w-32" />
                <IconButton :icon="['fas', 'check']" label="Add" @click="handleAddWeblink" />
                <IconButton :icon="['fas', 'xmark']" label="Cancel" class="text-(--text-muted)" @click="showAddWeblink = false" />
            </div>
        </div>

        <!-- Attachments (tile grid) -->
        <div v-if="attachments.length > 0">
            <div class="flex items-center justify-between mb-2">
                <SubHeader class="text-sm">{{ t('boards.attachments') }}</SubHeader>
                <IconButton v-if="!readonly" :icon="['fas', 'plus']" :label="t('boards.addAttachment')" class="text-(--text-muted) text-xs" @click="emit('addFile')" />
            </div>
            <div class="grid grid-cols-3 sm:grid-cols-4 gap-2">
                <div v-for="att in attachments" :key="att.id" class="relative group rounded-lg border border-(--border) overflow-hidden bg-(--bg-accent) cursor-pointer" style="aspect-ratio: 3/4" @click="canPreview(att) ? openPreview(att) : handleDownload(att)">
                    <!-- Image thumbnail -->
                    <img v-if="isImage(att.contentType) && srcFor(att.id)" :src="srcFor(att.id) ?? undefined" :alt="att.originalName" class="w-full h-full object-contain" />
                    <!-- File type icon fallback -->
                    <div v-else class="w-full h-full flex items-center justify-center">
                        <font-awesome-icon :icon="fileIcon(att)" class="text-3xl text-(--text-muted)" />
                    </div>
                    <!-- Filename overlay (top) -->
                    <div class="absolute top-0 left-0 right-0 bg-black/50 px-2 py-1">
                        <p class="text-[0.65rem] text-white truncate">{{ att.originalName }}</p>
                    </div>
                    <!-- Action bar (bottom) -->
                    <div class="absolute bottom-0 left-0 right-0 bg-black/50 px-1 py-0.5 flex items-center justify-between opacity-0 group-hover:opacity-100 transition-opacity">
                        <span class="text-[0.55rem] text-white/80">{{ formatFileSize(att.sizeBytes) }}</span>
                        <div class="flex gap-1" @click.stop>
                            <IconButton v-if="canPreview(att)" :icon="['fas', 'eye']" label="Preview" class="text-white text-xs" @click="openPreview(att)" />
                            <IconButton :icon="['fas', 'download']" label="Download" class="text-white text-xs" @click="handleDownload(att)" />
                            <IconButton v-if="!readonly" :icon="['fas', 'trash']" label="Remove" class="text-white text-xs" @click="removeAttachment(att.id)" />
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <!-- Preview overlay (fullscreen) -->
        <Teleport to="body">
            <div v-if="showPreview" class="fixed inset-0 z-50 flex items-center justify-center" @keydown.left="previewPrev" @keydown.right="previewNext" @keydown.escape="showPreview = false" tabindex="0" ref="previewOverlayRef">
                <div class="absolute inset-0 bg-black/60" @click="showPreview = false" />
                <!-- Left nav -->
                <div v-if="previewIndex > 0" class="absolute left-2 z-20 top-1/2 -translate-y-1/2">
                    <IconButton :icon="['fas', 'chevron-left']" label="Previous" class="text-white text-2xl bg-black/40 rounded-full p-3 hover:bg-black/60" @click="previewPrev" />
                </div>
                <!-- Right nav -->
                <div v-if="previewIndex < attachments.length - 1" class="absolute right-2 z-20 top-1/2 -translate-y-1/2">
                    <IconButton :icon="['fas', 'chevron-right']" label="Next" class="text-white text-2xl bg-black/40 rounded-full p-3 hover:bg-black/60" @click="previewNext" />
                </div>
                <div class="relative z-10 flex flex-col max-w-[90vw] max-h-[90vh] rounded-theme border border-(--border) bg-(--bg) shadow-xl overflow-hidden">
                    <!-- Header -->
                    <div class="flex items-center gap-2 px-4 py-2 border-b border-(--border) shrink-0">
                        <span class="text-sm font-medium flex-1 truncate">{{ previewName }}</span>
                        <span class="text-xs text-(--text-muted)">{{ previewIndex + 1 }}/{{ attachments.length }}</span>
                        <IconButton :icon="['fas', 'download']" label="Download" class="text-(--text-muted)" @click="downloadCurrent" />
                        <IconButton :icon="['fas', 'xmark']" label="Close" class="text-(--text-muted)" @click="showPreview = false" />
                    </div>
                    <!-- Content -->
                    <div class="flex-1 overflow-auto p-4 flex items-center justify-center">
                        <img v-if="previewUrl && !previewName.toLowerCase().endsWith('.pdf')" :src="previewUrl" :alt="previewName" class="max-w-full max-h-[80vh] rounded" />
                        <iframe v-else-if="previewUrl && previewName.toLowerCase().endsWith('.pdf')" :src="previewUrl" class="w-full h-[80vh] rounded" style="min-width: 60vw" />
                        <div v-else-if="previewCsv" class="overflow-auto max-h-[80vh] w-full">
                            <table class="w-full text-xs border-collapse">
                                <thead v-if="previewCsv.length > 0"><tr class="border-b border-(--border)"><th v-for="(cell, ci) in previewCsv[0]" :key="ci" class="px-2 py-1 text-left font-semibold bg-(--bg-accent) sticky top-0">{{ cell }}</th></tr></thead>
                                <tbody><tr v-for="(row, ri) in previewCsv.slice(1)" :key="ri" class="border-b border-(--border) last:border-0"><td v-for="(cell, ci) in row" :key="ci" class="px-2 py-1 whitespace-nowrap">{{ cell }}</td></tr></tbody>
                            </table>
                        </div>
                    </div>
                </div>
            </div>
        </Teleport>
    </div>
</template>
