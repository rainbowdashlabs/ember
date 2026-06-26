/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TicketTile from './TicketTile.vue'
import type { BoardLane, BoardTicket, BoardLabel } from '@/api/boards'
import type { MemberCompletion } from '@/api/stationMembers'

const props = defineProps<{
    lane: BoardLane
    tickets: BoardTicket[]
    archivedCount: number
    isLastLane: boolean
    dragTicket: BoardTicket | null
    dropLaneId: number | null
    dropPosition: number | null
    members: MemberCompletion[]
    shortKey: string
    labelsForTicket: (ticketId: number) => BoardLabel[]
}>()

const emit = defineEmits<{
    dragover: [laneId: number, event: DragEvent]
    dragleave: [event: DragEvent]
    drop: [laneId: number]
    'ticket-dragstart': [ticket: BoardTicket, event: DragEvent]
    'ticket-dragend': []
    'ticket-click': [ticket: BoardTicket]
    'navigate-archived': []
}>()

const { t } = useI18n()

function memberNameFor(ticket: BoardTicket): string | undefined {
    if (!ticket.assignee) return undefined
    return props.members.find(m => m.memberUid === ticket.assignee?.memberUid)?.name
}

function trailingDropVisible(): boolean {
    if (props.dropLaneId !== props.lane.id || !props.dragTicket || props.dropPosition === null) return false
    const remaining = props.tickets.filter(tk => tk.id !== props.dragTicket!.id).length
    return props.dropPosition >= remaining
}
</script>

<template>
    <div
        class="md:flex-1 md:min-w-[14rem] md:max-w-[24rem] bg-bg-light-accent dark:bg-bg-dark-accent border border-[var(--border)] rounded-lg p-3 border-t-2 transition-colors"
        :style="{ borderTopColor: lane.color ?? 'var(--primary)' }"
        :class="{ 'bg-primary/5': dropLaneId === lane.id && dragTicket }"
        @dragover="emit('dragover', lane.id, $event)"
        @dragleave="emit('dragleave', $event)"
        @drop="emit('drop', lane.id)"
    >
        <div class="flex items-center justify-between mb-3">
            <SubHeader class="text-sm text-[var(--text-muted)] uppercase tracking-wide">{{ lane.name }}</SubHeader>
            <BaseBadge bg-class="bg-[var(--bg)]" class="text-[var(--text-muted)]">{{ tickets.length }}</BaseBadge>
        </div>

        <div class="min-h-[3rem]">
            <template v-for="(ticket, idx) in tickets" :key="ticket.id">
                <div v-if="dropLaneId === lane.id && dropPosition === idx && dragTicket && dragTicket.id !== ticket.id"
                    class="h-12 mb-2 rounded-lg border-2 border-dashed border-primary/40 bg-primary/5" />
                <div
                    :data-ticket-id="ticket.id"
                    draggable="true"
                    class="mb-2"
                    :class="{ 'opacity-30': dragTicket?.id === ticket.id }"
                    @dragstart="emit('ticket-dragstart', ticket, $event)"
                    @dragend="emit('ticket-dragend')"
                >
                    <TicketTile
                        :ticket="ticket"
                        :short-key="shortKey"
                        :member-name="memberNameFor(ticket)"
                        :identity="ticket.assignee"
                        :labels="labelsForTicket(ticket.id)"
                        :attachment-count="ticket.attachmentCount"
                        @click="emit('ticket-click', ticket)"
                    />
                </div>
            </template>
            <div v-if="trailingDropVisible()"
                class="h-12 rounded-lg border-2 border-dashed border-primary/40 bg-primary/5" />
        </div>

        <div v-if="isLastLane && archivedCount > 0" class="mt-2">
            <SecondaryButton class="w-full text-xs" @click="emit('navigate-archived')">
                {{ archivedCount }} {{ t('boards.archived') }}
            </SecondaryButton>
        </div>

        <p v-if="tickets.length === 0 && !dragTicket" class="text-xs text-[var(--text-muted)] text-center py-4">
            {{ t('boards.noTickets') }}
        </p>
    </div>
</template>
