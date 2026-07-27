/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import type {EventBreak} from '@/api/types'

const {t} = useI18n()

const modelValue = defineModel<boolean>({required: true})

const props = defineProps<{
  eventBreak: EventBreak | null
}>()

const emit = defineEmits<{
  save: [data: { name: string; startDate: string; endDate: string }]
}>()

const breakName = ref('')
const breakStartDate = ref('')
const breakEndDate = ref('')

watch(modelValue, (open) => {
  if (!open) return
  const br = props.eventBreak
  if (br) {
    breakName.value = br.name ?? ''
    breakStartDate.value = br.startDate ?? ''
    breakEndDate.value = br.endDate ?? ''
  } else {
    breakName.value = ''
    breakStartDate.value = ''
    breakEndDate.value = ''
  }
})

function submit() {
  emit('save', {name: breakName.value, startDate: breakStartDate.value, endDate: breakEndDate.value})
}
</script>

<template>
  <Modal v-model="modelValue">
    <div class="space-y-4">
      <SubHeader>{{ eventBreak ? t('events.editBreak') : t('events.addBreak') }}</SubHeader>

      <div class="space-y-1">
        <FieldLabel>{{ t('events.breakName') }}</FieldLabel>
        <TextInput v-model="breakName" :placeholder="t('events.breakNamePlaceholder')"/>
      </div>

      <div class="grid grid-cols-2 gap-3">
        <div class="space-y-1">
          <FieldLabel>{{ t('events.breakStart') }}</FieldLabel>
          <DateInput v-model="breakStartDate"/>
        </div>
        <div class="space-y-1">
          <FieldLabel>{{ t('events.breakEnd') }}</FieldLabel>
          <DateInput v-model="breakEndDate"/>
        </div>
      </div>

      <div class="flex justify-end gap-3">
        <SecondaryButton @click="modelValue = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="!breakName || !breakStartDate || !breakEndDate" @click="submit">
          {{ t('common.save') }}
        </PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
