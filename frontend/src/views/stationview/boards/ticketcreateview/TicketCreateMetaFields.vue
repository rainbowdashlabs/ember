/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import MemberSelectInput from '@/components/input/select/MemberSelectInput.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import LabelSelectInput from '@/components/input/select/LabelSelectInput.vue'
import { fromCompletion } from '@/components/input/select/memberOption'
import type {BoardLabel, BoardLane, MemberCompletion, TicketPriority} from '@/api/generated/schema'
import {priorityOptions} from '@/util/ticketPriority'

const props = defineProps<{
    laneId: string
    priority: TicketPriority
    assignee: string
    dueDate: string
    createLaneOptions: BoardLane[]
    /** Whom the new ticket may be handed to, which is who the picker offers. */
    assignableMembers: MemberCompletion[]
    allLabels: BoardLabel[]
    selectedLabels: BoardLabel[]
}>()

const emit = defineEmits<{
    'update:laneId': [value: string]
    'update:priority': [value: TicketPriority]
    'update:assignee': [value: string]
    'update:dueDate': [value: string]
    toggleLabel: [id: number]
    createLabel: [name: string]
}>()

const { t } = useI18n()

const assignable = computed(() => props.assignableMembers.map(fromCompletion))
const priorities = computed(() => priorityOptions(t))
</script>

<template>
    <div class="space-y-4">
        <div>
            <FieldLabel class="mb-1">{{ t('boards.lanes') }}</FieldLabel>
            <SelectInput :model-value="laneId" class="w-full" @update:model-value="v => emit('update:laneId', String(v))">
                <option v-for="lane in createLaneOptions" :key="lane.id" :value="lane.id">{{ lane.name }}</option>
            </SelectInput>
        </div>
        <div>
            <FieldLabel class="mb-1">{{ t('boards.priority') }}</FieldLabel>
            <SelectInput :model-value="priority" class="w-full" @update:model-value="v => emit('update:priority', v as TicketPriority)">
                <option v-for="option in priorities" :key="option.value" :value="option.value">{{ option.label }}</option>
            </SelectInput>
        </div>
        <div>
            <FieldLabel class="mb-1">{{ t('boards.assignee') }}</FieldLabel>
            <MemberSelectInput :model-value="assignee" :members="assignable" :placeholder="t('boards.unassigned')" clearable @update:model-value="v => emit('update:assignee', String(v))" />
        </div>
        <div>
            <FieldLabel class="mb-1">{{ t('boards.dueDate') }}</FieldLabel>
            <DateInput :model-value="dueDate" @update:model-value="v => emit('update:dueDate', String(v))" />
        </div>
        <div v-if="allLabels.length > 0">
            <FieldLabel class="mb-1">{{ t('boards.labels') }}</FieldLabel>
            <LabelSelectInput :labels="allLabels" :selected="selectedLabels"
                              :placeholder="t('boards.labelsPlaceholder')" :empty-text="t('boards.noLabelsFound')"
                              @toggle="id => emit('toggleLabel', id)" @create="name => emit('createLabel', name)" />
        </div>
    </div>
</template>
