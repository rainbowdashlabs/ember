/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import Alert from '@/components/feedback/Alert.vue'
import LaneStatusMover from '../boardview/LaneStatusMover.vue'
import TicketLabelsField from './TicketLabelsField.vue'
import TicketPriorityField from './TicketPriorityField.vue'
import TicketAssigneeField from './TicketAssigneeField.vue'
import TicketDueDateField from './TicketDueDateField.vue'
import TicketCustomFields from './TicketCustomFields.vue'
import type {
    BoardLane, BoardLabel, BoardField, BoardTicket,
    TicketPriorityName, BoardFieldTypeName,
} from '@/api/boards'
import type { MemberCompletion } from '@/api/stationMembers'
import type {PriorityOption} from './types'
import { formatDateTime } from '@/util/format'


const props = defineProps<{
    ticket: BoardTicket
    lanes: BoardLane[]
    members: MemberCompletion[]
    assignableMembers: MemberCompletion[]
    allLabels: BoardLabel[]
    ticketLabels: BoardLabel[]
    boardFields: BoardField[]
    priorityOptions: PriorityOption[]
    canEdit: boolean
    error: string
}>()

const priority = defineModel<TicketPriorityName>('priority')
const assignedMemberId = defineModel<string>('assignedMemberId', { default: '' })
const dueDate = defineModel<string>('dueDate', { default: '' })
const fieldValues = defineModel<Record<number, unknown>>('fieldValues', { default: () => ({}) })

const emit = defineEmits<{
    moveTo: [laneId: number]
    saveTicket: []
    toggleLabel: [id: number]
    createLabel: [name: string]
    saveField: [fieldId: number, fieldType: BoardFieldTypeName, value: unknown]
}>()

const editingPriority = ref(false)
const editingAssignee = ref(false)
const editingDueDate = ref(false)
const rootRef = ref<HTMLElement | null>(null)
const laneMoverRef = ref<InstanceType<typeof LaneStatusMover> | null>(null)

function closeAllEditors() {
    laneMoverRef.value?.close()
    editingPriority.value = false
    editingAssignee.value = false
    editingDueDate.value = false
}

function handleClickOutside(e: MouseEvent) {
    if (rootRef.value && !rootRef.value.contains(e.target as Node)) {
        closeAllEditors()
    }
}

onMounted(() => document.addEventListener('click', handleClickOutside))
onBeforeUnmount(() => document.removeEventListener('click', handleClickOutside))
</script>

<template>
    <div ref="rootRef" class="space-y-4">
        <LaneStatusMover ref="laneMoverRef" :lanes="lanes" :current-lane-id="ticket.laneId" :can-edit="canEdit" @move="emit('moveTo', $event)" />
        <TicketLabelsField :all-labels="allLabels" :ticket-labels="ticketLabels" :can-edit="canEdit" @toggle="emit('toggleLabel', $event)" @create="emit('createLabel', $event)" />
        <TicketPriorityField
            v-model:priority="priority"
            v-model:editing="editingPriority"
            :options="priorityOptions"
            :can-edit="canEdit"
            @save="emit('saveTicket')"
            @open="closeAllEditors"
        />
        <TicketAssigneeField
            v-model:assigned-member-id="assignedMemberId"
            v-model:editing="editingAssignee"
            :ticket="ticket"
            :members="members"
            :assignable-members="assignableMembers"
            :can-edit="canEdit"
            @save="emit('saveTicket')"
            @open="closeAllEditors"
        />
        <TicketDueDateField
            v-model:due-date="dueDate"
            v-model:editing="editingDueDate"
            :can-edit="canEdit"
            @save="emit('saveTicket')"
            @open="closeAllEditors"
        />
        <TicketCustomFields
            v-model:field-values="fieldValues"
            :fields="boardFields"
            :members="members"
            :can-edit="canEdit"
            @save="(id, type, value) => emit('saveField', id, type, value)"
        />
        <div class="text-xs text-[var(--text-muted)] space-y-1 pt-4 border-t border-[var(--border)]">
            <p v-if="ticket.createdAt">Erstellt: {{ formatDateTime(ticket.createdAt) }}</p>
            <p v-if="ticket.updatedAt">Geändert: {{ formatDateTime(ticket.updatedAt) }}</p>
        </div>
        <Alert v-if="error" variant="error">{{ error }}</Alert>
    </div>
</template>
