/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { computed } from 'vue'
import type { BoardTicket, BoardLabel } from '@/api/boards'
import { TicketPriority } from '@/api/boards'
import UserAvatar from '@/components/avatar/UserAvatar.vue'
import { contrastTextColor } from '@/theme/contrast'
import type { MemberIdentity } from '@/api/types'

const props = defineProps<{
    ticket: BoardTicket
    shortKey: string
    memberName?: string
    identity?: MemberIdentity | null
    labels?: BoardLabel[]
    attachmentCount?: number
}>()

const emit = defineEmits<{
    click: [ticket: BoardTicket]
}>()

const priorityIcon = computed(() => {
    switch (props.ticket.priority) {
        case TicketPriority.HIGHEST: return ['fas', 'angles-up']
        case TicketPriority.HIGH: return ['fas', 'angle-up']
        case TicketPriority.MEDIUM: return ['fas', 'equals']
        case TicketPriority.LOW: return ['fas', 'angle-down']
        case TicketPriority.LOWEST: return ['fas', 'angles-down']
        default: return ['fas', 'minus']
    }
})

const priorityColor = computed(() => {
    switch (props.ticket.priority) {
        case TicketPriority.HIGHEST: return 'text-red-500'
        case TicketPriority.HIGH: return 'text-orange-500'
        case TicketPriority.MEDIUM: return 'text-yellow-500'
        case TicketPriority.LOW: return 'text-blue-400'
        case TicketPriority.LOWEST: return 'text-gray-400'
        default: return 'text-gray-400'
    }
})

const daysInLane = computed(() => {
    const entered = new Date(props.ticket.laneEnteredAt)
    const now = new Date()
    return Math.floor((now.getTime() - entered.getTime()) / (1000 * 60 * 60 * 24))
})

const timeDots = computed(() => {
    const d = daysInLane.value
    if (d < 1) return { count: 0, color: '' }
    if (d < 8) {
        const dots = d >= 7 ? 4 : d >= 5 ? 3 : d >= 3 ? 2 : 1
        return { count: dots, color: 'bg-green-500' }
    }
    if (d < 15) {
        const dots = d >= 14 ? 4 : d >= 12 ? 3 : d >= 10 ? 2 : 1
        return { count: dots, color: 'bg-orange-500' }
    }
    const dots = d >= 60 ? 4 : d >= 30 ? 3 : d >= 20 ? 2 : 1
    return { count: dots, color: 'bg-red-500' }
})

const hasChecklist = computed(() => props.ticket.checklistTotal > 0)
const checklistPercent = computed(() =>
    hasChecklist.value ? Math.round((props.ticket.checklistChecked / props.ticket.checklistTotal) * 100) : 0
)

const formattedDueDate = computed(() => {
    if (!props.ticket.dueDate) return null
    return new Date(props.ticket.dueDate).toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit' })
})

const isOverdue = computed(() => {
    if (!props.ticket.dueDate) return false
    return new Date(props.ticket.dueDate) < new Date(new Date().toDateString())
})
</script>

<template>
    <div
        class="bg-[var(--bg)] border border-[var(--border)] rounded-lg p-3 cursor-pointer hover:border-[var(--accent)] transition-colors shadow-sm"
        @click="emit('click', ticket)"
    >
        <!-- Title -->
        <p class="text-sm font-medium leading-tight mb-1 line-clamp-2">{{ ticket.title }}</p>

        <!-- Labels -->
        <div v-if="labels && labels.length > 0" class="flex flex-wrap gap-1 mb-2">
            <span v-for="label in labels" :key="label.id" class="text-[0.65rem] leading-tight px-1.5 py-0.5 rounded-full font-medium" :style="{ backgroundColor: label.color, color: contrastTextColor(label.color) }">{{ label.name }}</span>
        </div>

        <!-- Checklist progress -->
        <div v-if="hasChecklist" class="flex items-center gap-2 mb-2">
            <div class="flex-1 h-1.5 bg-[var(--bg-muted)] rounded-full overflow-hidden">
                <div
                    class="h-full rounded-full transition-all"
                    :class="checklistPercent === 100 ? 'bg-green-500' : 'bg-primary'"
                    :style="{ width: checklistPercent + '%' }"
                />
            </div>
            <span class="text-xs text-[var(--text-muted)] whitespace-nowrap">{{ ticket.checklistChecked }}/{{ ticket.checklistTotal }}</span>
        </div>

        <!-- Bottom row -->
        <div class="flex items-center justify-between text-xs text-[var(--text-muted)]">
            <div class="flex items-center gap-2">
                <!-- Time dots -->
                <div v-if="timeDots.count > 0" class="flex gap-0.5">
                    <span v-for="i in timeDots.count" :key="i" :class="timeDots.color" class="w-1.5 h-1.5 rounded-full" />
                </div>
                <!-- Priority -->
                <font-awesome-icon :icon="priorityIcon" :class="priorityColor" class="text-xs" />
                <!-- Due date -->
                <span v-if="formattedDueDate" class="flex items-center gap-0.5" :class="isOverdue ? 'text-red-500 font-medium' : ''">
                    <font-awesome-icon :icon="['fas', 'calendar']" class="text-[0.6rem]" />
                    {{ formattedDueDate }}
                </span>
                <!-- Attachments -->
                <span v-if="attachmentCount && attachmentCount > 0" class="flex items-center gap-0.5">
                    <font-awesome-icon :icon="['fas', 'paperclip']" class="text-[0.6rem]" />
                    {{ attachmentCount }}
                </span>
            </div>
            <div class="flex items-center gap-2">
                <span class="font-mono opacity-60">{{ shortKey }}-{{ ticket.ticketNumber }}</span>
                <UserAvatar v-if="ticket.assignee" :identity="identity" :name="memberName" size="sm" />
            </div>
        </div>
    </div>
</template>
