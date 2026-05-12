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
import SectionHeader from '@/components/typography/SectionHeader.vue'
import type {EventBreak} from '@/api/types'

const {t} = useI18n()

const props = defineProps<{
  modelValue: boolean
  eventBreak: EventBreak | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  save: [data: { name: string; startDate: string; endDate: string }]
}>()

const breakName = ref('')
const breakStartDate = ref('')
const breakEndDate = ref('')
const saving = ref(false)

watch(() => props.modelValue, (open) => {
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
  saving.value = true
  emit('save', {name: breakName.value, startDate: breakStartDate.value, endDate: breakEndDate.value})
  saving.value = false
}
</script>

<template>
  <Modal :model-value="modelValue" @update:model-value="emit('update:modelValue', $event)">
    <div class="space-y-4">
      <SectionHeader>{{ eventBreak ? t('events.editBreak') : t('events.addBreak') }}</SectionHeader>

      <div class="space-y-1">
        <label class="block text-sm font-medium">{{ t('events.breakName') }}</label>
        <TextInput v-model="breakName" :placeholder="t('events.breakNamePlaceholder')"/>
      </div>

      <div class="grid grid-cols-2 gap-3">
        <div class="space-y-1">
          <label class="block text-sm font-medium">{{ t('events.breakStart') }}</label>
          <DateInput v-model="breakStartDate"/>
        </div>
        <div class="space-y-1">
          <label class="block text-sm font-medium">{{ t('events.breakEnd') }}</label>
          <DateInput v-model="breakEndDate"/>
        </div>
      </div>

      <div class="flex justify-end gap-3">
        <SecondaryButton @click="emit('update:modelValue', false)">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="saving || !breakName || !breakStartDate || !breakEndDate" @click="submit">
          {{ saving ? t('common.loading') : t('common.save') }}
        </PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
