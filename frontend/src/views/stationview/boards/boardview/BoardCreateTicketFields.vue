/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import MemberSelectInput from '@/components/input/select/MemberSelectInput.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import MarkdownEditor from '@/components/input/MarkdownEditor.vue'
import { TicketPriority } from '@/api/boards'
import type { BoardLane, TicketPriorityName } from '@/api/boards'
import type { MemberCompletion } from '@/api/stationMembers'

const title = defineModel<string>('title', { required: true })
const description = defineModel<string>('description', { required: true })
const laneId = defineModel<string>('laneId', { required: true })
const priority = defineModel<TicketPriorityName>('priority', { required: true })
const assignee = defineModel<string>('assignee', { required: true })
const dueDate = defineModel<string>('dueDate', { required: true })

defineProps<{
    laneOptions: BoardLane[]
    members: MemberCompletion[]
}>()

const { t } = useI18n()
</script>

<template>
    <div>
        <FieldLabel class="mb-1">{{ t('boards.ticketTitle') }} *</FieldLabel>
        <TextInput v-model="title" />
    </div>
    <div>
        <FieldLabel class="mb-1">{{ t('boards.ticketDescription') }}</FieldLabel>
        <MarkdownEditor v-model="description" :placeholder="t('boards.ticketDescription')" />
    </div>
    <div class="grid grid-cols-2 gap-4">
        <div>
            <FieldLabel class="mb-1">{{ t('boards.lanes') }}</FieldLabel>
            <SelectInput v-model="laneId" class="w-full">
                <option v-for="lane in laneOptions" :key="lane.id" :value="lane.id">{{ lane.name }}</option>
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
    </div>
    <div class="grid grid-cols-2 gap-4">
        <div>
            <FieldLabel class="mb-1">{{ t('boards.assignee') }}</FieldLabel>
            <MemberSelectInput v-model="assignee" :members="members" :placeholder="t('boards.unassigned')" />
        </div>
        <div>
            <FieldLabel class="mb-1">{{ t('boards.dueDate') }}</FieldLabel>
            <DateInput v-model="dueDate" />
        </div>
    </div>
</template>
