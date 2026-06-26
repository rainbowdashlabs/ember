/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import TicketCreateMetaFields from './TicketCreateMetaFields.vue'
import type { TicketPriorityName, BoardLane, BoardLabel } from '@/api/boards'
import type { MemberCompletion } from '@/api/stationMembers'

defineProps<{
    createLaneOptions: BoardLane[]
    members: MemberCompletion[]
    allLabels: BoardLabel[]
    selectedLabels: BoardLabel[]
    error: string
    submitting: boolean
    cancelTo: string
}>()

const laneId = defineModel<string>('laneId', { required: true })
const priority = defineModel<TicketPriorityName>('priority', { required: true })
const assignee = defineModel<string>('assignee', { required: true })
const dueDate = defineModel<string>('dueDate', { required: true })

const emit = defineEmits<{
    (e: 'toggleLabel', id: number): void
    (e: 'createLabel', name: string): void
    (e: 'cancel'): void
    (e: 'submit'): void
}>()

const { t } = useI18n()
</script>

<template>
    <div class="space-y-4">
        <TicketCreateMetaFields
            :lane-id="laneId"
            :priority="priority"
            :assignee="assignee"
            :due-date="dueDate"
            :create-lane-options="createLaneOptions"
            :members="members"
            :all-labels="allLabels"
            :selected-labels="selectedLabels"
            @update:lane-id="v => laneId = v"
            @update:priority="v => priority = v"
            @update:assignee="v => assignee = v"
            @update:due-date="v => dueDate = v"
            @toggle-label="id => emit('toggleLabel', id)"
            @create-label="name => emit('createLabel', name)"
        />
        <Alert v-if="error" variant="error">{{ error }}</Alert>
        <div class="flex gap-2">
            <SecondaryButton class="flex-1" @click="emit('cancel')">{{ t('common.cancel') }}</SecondaryButton>
            <PrimaryButton class="flex-1" :disabled="submitting" @click="emit('submit')">
                <Spinner v-if="submitting" size="sm" class="mr-1" />
                {{ t('common.create') }}
            </PrimaryButton>
        </div>
    </div>
</template>
