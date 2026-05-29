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
import IconButton from '@/components/button/IconButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import { contrastTextColor } from '@/theme/contrast'
import UserAvatar from '@/components/avatar/UserAvatar.vue'
import { boards, stationMembers } from '@/api'
import type { Board, BoardTicket, BoardLabel } from '@/api/boards'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const boardKey = computed(() => route.params.boardKey as string)
const board = ref<Board | null>(null)
const tickets = ref<BoardTicket[]>([])
const members = ref<{ id: number; name: string }[]>([])
const allLabels = ref<BoardLabel[]>([])
const ticketLabelMap = ref<Map<number, number[]>>(new Map())
const labelFilter = ref<Set<number>>(new Set())
const loading = ref(true)
const error = ref('')

async function loadData() {
    loading.value = true
    try {
        const [b, lns, tks, m, lb, tlm] = await Promise.all([
            boards.getBoard(boardKey.value), boards.getLanes(boardKey.value), boards.listTickets(boardKey.value),
            stationMembers.listCompletions(), boards.getLabels(boardKey.value), boards.getAllTicketLabels(boardKey.value),
        ])
        board.value = b; members.value = m; allLabels.value = lb
        const map = new Map<number, number[]>()
        for (const { ticketId, labelId } of tlm) { if (!map.has(ticketId)) map.set(ticketId, []); map.get(ticketId)!.push(labelId) }
        ticketLabelMap.value = map
        // Find last visible lane and filter for archived tickets
        const visibleLanes = lns.filter(l => !b.backlogLaneId || l.id !== b.backlogLaneId)
        const lastLane = visibleLanes.length > 0 ? visibleLanes[visibleLanes.length - 1] : null
        if (lastLane && b.hideDoneAfterDays > 0) {
            const cutoff = new Date(); cutoff.setDate(cutoff.getDate() - b.hideDoneAfterDays)
            tickets.value = tks.filter(tk => tk.laneId === lastLane.id && new Date(tk.laneEnteredAt) < cutoff)
        }
    } catch { error.value = t('common.error') }
    finally { loading.value = false }
}

const filteredTickets = computed(() => {
    if (labelFilter.value.size === 0) return tickets.value
    return tickets.value.filter(t => { const ids = ticketLabelMap.value.get(t.id) ?? []; return ids.some(id => labelFilter.value.has(id)) })
})

function labelsForTicket(ticketId: number): BoardLabel[] { return allLabels.value.filter(l => (ticketLabelMap.value.get(ticketId) ?? []).includes(l.id)) }
function toggleLabelFilter(id: number) { const n = new Set(labelFilter.value); if (n.has(id)) n.delete(id); else n.add(id); labelFilter.value = n }
function priorityIcon(p: string) { return { HIGHEST: ['fas','angles-up'], HIGH: ['fas','angle-up'], MEDIUM: ['fas','equals'], LOW: ['fas','angle-down'], LOWEST: ['fas','angles-down'] }[p] ?? ['fas','minus'] }
function priorityColor(p: string) { return { HIGHEST: 'text-red-500', HIGH: 'text-orange-500', MEDIUM: 'text-yellow-500', LOW: 'text-blue-400', LOWEST: 'text-gray-400' }[p] ?? 'text-gray-400' }
function memberName(id: number | null) { return id ? members.value.find(m => m.id === id)?.name ?? '' : '' }

onMounted(loadData)
</script>

<template>
    <ViewContent>
        <Spinner v-if="loading" />
        <Alert v-else-if="error" variant="error">{{ error }}</Alert>
        <template v-else-if="board">
            <div class="flex items-center gap-3 mb-4">
                <IconButton :icon="['fas', 'chevron-left']" label="Back" @click="router.push(`/station/boards/${board.shortKey}`)" />
                <SectionHeader>{{ board.name }} — {{ t('boards.archived') }}</SectionHeader>
            </div>

            <div v-if="allLabels.length > 0" class="flex flex-wrap gap-1 mb-4 items-center">
                <span v-for="label in allLabels" :key="label.id" class="text-xs px-2 py-0.5 rounded-full cursor-pointer transition-all" :class="labelFilter.has(label.id) ? 'ring-2 ring-offset-1 ring-[var(--text)]' : 'opacity-70 hover:opacity-100'" :style="{ backgroundColor: label.color, color: contrastTextColor(label.color) }" @click="toggleLabelFilter(label.id)">{{ label.name }}</span>
                <span v-if="labelFilter.size > 0" class="text-xs text-(--text-muted) cursor-pointer ml-1" @click="labelFilter = new Set()"><font-awesome-icon :icon="['fas', 'xmark']" class="text-[0.6rem]" /></span>
            </div>

            <EmptyState v-if="filteredTickets.length === 0">{{ t('boards.noTickets') }}</EmptyState>
            <table v-else class="w-full text-sm">
                <thead><tr class="text-left text-xs text-(--text-muted) uppercase border-b border-(--border)">
                    <th class="py-2 pr-3">ID</th><th class="py-2 pr-3 w-full">{{ t('boards.ticketTitle') }}</th><th class="py-2 pr-3">Labels</th><th class="py-2 pr-3">{{ t('boards.priority') }}</th><th class="py-2 pr-3">{{ t('boards.assignee') }}</th><th class="py-2">{{ t('boards.dueDate') }}</th>
                </tr></thead>
                <tbody>
                    <tr v-for="ticket in filteredTickets" :key="ticket.id" class="border-b border-(--border) last:border-0 cursor-pointer hover:bg-primary/5" @click="router.push(`/station/boards/${board.shortKey}/tickets/${ticket.ticketNumber}`)">
                        <td class="py-2 pr-3 font-mono text-(--text-muted) whitespace-nowrap">{{ board.shortKey }}-{{ ticket.ticketNumber }}</td>
                        <td class="py-2 pr-3">{{ ticket.title }}</td>
                        <td class="py-2 pr-3"><div class="flex gap-1"><span v-for="l in labelsForTicket(ticket.id)" :key="l.id" class="text-[0.6rem] px-1.5 py-0.5 rounded-full" :style="{ backgroundColor: l.color, color: contrastTextColor(l.color) }">{{ l.name }}</span></div></td>
                        <td class="py-2 pr-3"><font-awesome-icon :icon="priorityIcon(ticket.priority)" :class="priorityColor(ticket.priority)" class="text-xs" /></td>
                        <td class="py-2 pr-3"><div v-if="ticket.assignedMemberId" class="flex items-center gap-1"><UserAvatar :member-id="ticket.assignedMemberId" size="sm" /><span class="text-xs whitespace-nowrap">{{ memberName(ticket.assignedMemberId) }}</span></div></td>
                        <td class="py-2 text-xs whitespace-nowrap">{{ ticket.dueDate ? new Date(ticket.dueDate).toLocaleDateString('de-DE') : '' }}</td>
                    </tr>
                </tbody>
            </table>
        </template>
    </ViewContent>
</template>
