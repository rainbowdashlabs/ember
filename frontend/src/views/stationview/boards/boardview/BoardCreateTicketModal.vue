/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import Modal from '@/components/feedback/Modal.vue'
import Alert from '@/components/feedback/Alert.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import BoardCreateTicketFields from './BoardCreateTicketFields.vue'
import { boards } from '@/api'
import {TicketPriority, type BoardLane, type TicketPriorityName} from '@/api/boards'
import type { MemberCompletion } from '@/api/stationMembers'
import { useAsyncAction } from '@/composables/useAsyncAction'

const open = defineModel<boolean>({ required: true })

const props = defineProps<{
    boardKey: string
    shortKey: string
    laneOptions: BoardLane[]
    defaultLaneId: number | null
    members: MemberCompletion[]
}>()

const { t } = useI18n()
const router = useRouter()

const title = ref('')
const description = ref('')
const laneId = ref('')
const priority = ref<TicketPriorityName>(TicketPriority.MEDIUM)
const assignee = ref('')
const dueDate = ref('')
const validationError = ref('')

watch(() => props.defaultLaneId, (id) => {
    if (!laneId.value && id) laneId.value = String(id)
}, { immediate: true })

const { error: apiError, run: runCreateTicket } = useAsyncAction(async () => {
    const created = await boards.createTicket(props.boardKey, {
        laneId: Number(laneId.value),
        title: title.value.trim(),
        description: description.value.trim() || undefined,
        priority: priority.value,
        assignedMemberId: assignee.value ? Number(assignee.value) : undefined,
        dueDate: dueDate.value || undefined,
    })
    open.value = false
    title.value = ''
    description.value = ''
    priority.value = TicketPriority.MEDIUM
    assignee.value = ''
    dueDate.value = ''
    router.push(`/station/boards/${props.boardKey}/tickets/${created.ticketNumber}`)
}, {formatError: () => t('common.error')})

const error = computed(() => validationError.value || apiError.value)

function submit() {
    validationError.value = ''
    if (!title.value.trim()) {
        validationError.value = t('common.requiredField')
        return
    }
    void runCreateTicket()
}

function openFullEditor() {
    open.value = false
    router.push({
        path: `/station/boards/${props.shortKey}/tickets/new`,
        query: {
            title: title.value || undefined,
            description: description.value || undefined,
            laneId: laneId.value || undefined,
            priority: priority.value !== TicketPriority.MEDIUM ? priority.value : undefined,
            assignee: assignee.value || undefined,
            dueDate: dueDate.value || undefined,
        },
    })
}
</script>

<template>
    <Modal v-model="open">
        <SubHeader class="mb-4">{{ t('boards.createTicket') }}</SubHeader>
        <div class="space-y-4">
            <BoardCreateTicketFields
                v-model:title="title"
                v-model:description="description"
                v-model:lane-id="laneId"
                v-model:priority="priority"
                v-model:assignee="assignee"
                v-model:due-date="dueDate"
                :lane-options="laneOptions"
                :members="members"
            />
            <Alert v-if="error" variant="error">{{ error }}</Alert>
            <div class="flex items-center justify-between">
                <SecondaryButton @click="openFullEditor">
                    <font-awesome-icon :icon="['fas', 'expand']" class="mr-1" />
                    {{ t('boards.moreOptions') }}
                </SecondaryButton>
                <PrimaryButton @click="submit">{{ t('common.create') }}</PrimaryButton>
            </div>
        </div>
    </Modal>
</template>
