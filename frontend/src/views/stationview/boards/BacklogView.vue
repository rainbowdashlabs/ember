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
import UserAvatar from '@/components/avatar/UserAvatar.vue'
import { boards, stationMembers } from '@/api'
import type { MemberCompletion } from '@/api/stationMembers'
import type { Board, BoardTicket } from '@/api/boards'
// Priority helpers use raw strings, no enum import needed

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const boardKey = computed(() => route.params.boardKey as string)
const board = ref<Board | null>(null)
const tickets = ref<BoardTicket[]>([])
const members = ref<MemberCompletion[]>([])
const loading = ref(true)
const error = ref('')

async function loadData() {
    loading.value = true
    try {
        const [b, t, m] = await Promise.all([boards.getBoard(boardKey.value), boards.listTickets(boardKey.value), stationMembers.listCompletions()])
        board.value = b
        members.value = m
        if (b.backlogLaneId) {
            tickets.value = t.filter(tk => tk.laneId === b.backlogLaneId).sort((a, b) => a.position - b.position)
        }
    } catch { error.value = t('common.error') }
    finally { loading.value = false }
}

function priorityIcon(p: string) { return { HIGHEST: ['fas','angles-up'], HIGH: ['fas','angle-up'], MEDIUM: ['fas','equals'], LOW: ['fas','angle-down'], LOWEST: ['fas','angles-down'] }[p] ?? ['fas','minus'] }
function priorityColor(p: string) { return { HIGHEST: 'text-red-500', HIGH: 'text-orange-500', MEDIUM: 'text-yellow-500', LOW: 'text-blue-400', LOWEST: 'text-gray-400' }[p] ?? 'text-gray-400' }


onMounted(loadData)
</script>

<template>
    <ViewContent>
        <Spinner v-if="loading" />
        <Alert v-else-if="error" variant="error">{{ error }}</Alert>
        <template v-else-if="board">
            <div class="flex items-center gap-3 mb-6">
                <IconButton :icon="['fas', 'chevron-left']" label="Back" @click="router.push(`/station/boards/${board.shortKey}`)" />
                <SectionHeader>{{ board.name }} — {{ t('boards.backlogTitle') }}</SectionHeader>
            </div>

            <EmptyState v-if="tickets.length === 0">{{ t('boards.noTickets') }}</EmptyState>

            <table v-else class="w-full text-sm">
                <thead>
                    <tr class="text-left text-xs text-(--text-muted) uppercase border-b border-(--border)">
                        <th class="py-2 pr-3">ID</th>
                        <th class="py-2 pr-3 w-full">{{ t('boards.ticketTitle') }}</th>
                        <th class="py-2 pr-3">{{ t('boards.priority') }}</th>
                        <th class="py-2 pr-3">{{ t('boards.assignee') }}</th>
                        <th class="py-2">{{ t('boards.dueDate') }}</th>
                    </tr>
                </thead>
                <tbody>
                    <tr v-for="ticket in tickets" :key="ticket.id"
                        class="border-b border-(--border) last:border-0 cursor-pointer hover:bg-primary/5"
                        @click="router.push(`/station/boards/${board.shortKey}/tickets/${ticket.ticketNumber}`)">
                        <td class="py-2 pr-3 font-mono text-(--text-muted) whitespace-nowrap">{{ board.shortKey }}-{{ ticket.ticketNumber }}</td>
                        <td class="py-2 pr-3">{{ ticket.title }}</td>
                        <td class="py-2 pr-3"><font-awesome-icon :icon="priorityIcon(ticket.priority)" :class="priorityColor(ticket.priority)" class="text-xs" /></td>
                        <td class="py-2 pr-3">
                            <div v-if="ticket.assignee" class="flex items-center gap-1">
                                <UserAvatar :identity="ticket.assignee" size="sm" />
                                <span class="text-xs whitespace-nowrap">{{ members.find(m => m.memberUid === ticket.assignee?.memberUid)?.name ?? '' }}</span>
                            </div>
                        </td>
                        <td class="py-2 text-xs whitespace-nowrap">{{ ticket.dueDate ? new Date(ticket.dueDate).toLocaleDateString('de-DE') : '' }}</td>
                    </tr>
                </tbody>
            </table>
        </template>
    </ViewContent>
</template>
