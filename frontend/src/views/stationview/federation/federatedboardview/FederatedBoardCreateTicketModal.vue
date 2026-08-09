/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import Alert from '@/components/feedback/Alert.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import MarkdownEditor from '@/components/input/MarkdownEditor.vue'
import {TicketPriority, type BoardLane, type TicketPriorityName} from '@/api/boards'

const modelValue = defineModel<boolean>({required: true})
const title = defineModel<string>('title', {required: true})
const description = defineModel<string>('description', {required: true})
const laneId = defineModel<string>('laneId', {required: true})
const priority = defineModel<TicketPriorityName>('priority', {required: true})

defineProps<{
  laneOptions: BoardLane[]
  error: string
}>()

const emit = defineEmits<{
  create: []
}>()

const {t} = useI18n()
</script>

<template>
  <Modal v-model="modelValue">
    <SubHeader class="mb-4">{{ t('boards.createTicket') }}</SubHeader>
    <div class="space-y-4">
      <div>
        <FieldLabel class="mb-1">{{ t('boards.ticketTitle') }} *</FieldLabel>
        <TextInput v-model="title"/>
      </div>
      <div>
        <FieldLabel class="mb-1">{{ t('boards.ticketDescription') }}</FieldLabel>
        <MarkdownEditor v-model="description" :placeholder="t('boards.ticketDescription')"/>
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
      <Alert v-if="error" variant="error">{{ error }}</Alert>
      <div class="flex justify-end">
        <PrimaryButton @click="emit('create')">{{ t('common.create') }}</PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
