/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import MemberSelectInput from '@/components/input/select/MemberSelectInput.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import LabelSelectInput from '@/components/input/select/LabelSelectInput.vue'
import {TicketPriority, type BoardLabel, type BoardLane, type TicketPriorityName} from '@/api/boards'
import type { MemberCompletion } from '@/api/stationMembers'

defineProps<{
    laneId: string
    priority: TicketPriorityName
    assignee: string
    dueDate: string
    createLaneOptions: BoardLane[]
    members: MemberCompletion[]
    allLabels: BoardLabel[]
    selectedLabels: BoardLabel[]
}>()

const emit = defineEmits<{
    (e: 'update:laneId', value: string): void
    (e: 'update:priority', value: TicketPriorityName): void
    (e: 'update:assignee', value: string): void
    (e: 'update:dueDate', value: string): void
    (e: 'toggleLabel', id: number): void
    (e: 'createLabel', name: string): void
}>()

const { t } = useI18n()
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
            <SelectInput :model-value="priority" class="w-full" @update:model-value="v => emit('update:priority', v as TicketPriorityName)">
                <option :value="TicketPriority.LOWEST">{{ t('boards.priorityLowest') }}</option>
                <option :value="TicketPriority.LOW">{{ t('boards.priorityLow') }}</option>
                <option :value="TicketPriority.MEDIUM">{{ t('boards.priorityMedium') }}</option>
                <option :value="TicketPriority.HIGH">{{ t('boards.priorityHigh') }}</option>
                <option :value="TicketPriority.HIGHEST">{{ t('boards.priorityHighest') }}</option>
            </SelectInput>
        </div>
        <div>
            <FieldLabel class="mb-1">{{ t('boards.assignee') }}</FieldLabel>
            <MemberSelectInput :model-value="assignee" :members="members" :placeholder="t('boards.unassigned')" @update:model-value="v => emit('update:assignee', String(v))" />
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
