/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import DateTimeInput from '@/components/input/datetime/DateTimeInput.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import {instantToLocalInput, localInputToInstant} from '@/util/format'

/**
 * When the sheet about to be opened runs, and what it is called.
 *
 * <p>A sheet used to begin and end at the moment it was made, which counted everybody on it for
 * nothing and left no way to write down a weekend at all. The two ends are asked for here instead,
 * prefilled so that the ordinary evening is still one further click, and each of them carries its
 * day, which is what makes a sheet over several days possible without an appointment behind it.
 */
const {t} = useI18n()

const open = defineModel<boolean>({required: true})

const props = defineProps<{
  templateName: string
  /** When the sheet is offered to begin and end, as instants. */
  suggestedStart: string
  suggestedEnd: string
  busy?: boolean
}>()

const emit = defineEmits<{
  create: [payload: {title: string; startTime: string; endTime: string}]
}>()

const title = ref('')
const start = ref('')
const end = ref('')

watch(open, (isOpen) => {
  if (!isOpen) return
  title.value = props.templateName
  start.value = instantToLocalInput(props.suggestedStart)
  end.value = instantToLocalInput(props.suggestedEnd)
}, {immediate: true})

function create() {
  const startTime = localInputToInstant(start.value)
  const endTime = localInputToInstant(end.value)
  if (!startTime || !endTime) return
  emit('create', {title: title.value, startTime, endTime})
}
</script>

<template>
  <Modal v-model="open" size="sm">
    <div class="space-y-4">
      <SectionHeader>{{ t('attendanceNew.modalTitle') }}</SectionHeader>
      <MutedText tag="p" size="sm">{{ t('attendanceNew.modalHint') }}</MutedText>

      <div class="space-y-1">
        <FieldLabel>{{ t('attendanceSession.title') }}</FieldLabel>
        <TextInput v-model="title" data-testid="new-session-title"/>
      </div>
      <div class="grid gap-3 sm:grid-cols-2">
        <div class="space-y-1">
          <FieldLabel>{{ t('attendanceSession.startTime') }}</FieldLabel>
          <DateTimeInput v-model="start" data-testid="new-session-start"/>
        </div>
        <div class="space-y-1">
          <FieldLabel>{{ t('attendanceSession.endTime') }}</FieldLabel>
          <DateTimeInput v-model="end" data-testid="new-session-end"/>
        </div>
      </div>

      <div class="flex justify-end gap-2">
        <SecondaryButton data-cancel @click="open = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="busy || !start || !end" data-testid="new-session-create" @click="create">
          {{ t('attendanceNew.create') }}
        </PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
