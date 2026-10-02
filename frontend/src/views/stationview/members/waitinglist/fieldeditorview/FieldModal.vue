/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import Modal from '@/components/feedback/Modal.vue'
import FieldModalForm from './FieldModalForm.vue'
import type {FieldType} from '@/api/generated/schema'
import type {QuestionSettingsModel} from '@/components/input/questionsettings/questionSettings'

const modelValue = defineModel<boolean>({required: true})
const fieldName = defineModel<string>('fieldName', {required: true})
const fieldType = defineModel<FieldType>('fieldType', {required: true})
const fieldSettings = defineModel<QuestionSettingsModel>('fieldSettings', {required: true})
const fieldPublic = defineModel<boolean>('fieldPublic', {required: true})

defineProps<{
  isEdit: boolean
  saving: boolean
}>()

const emit = defineEmits<{
  (e: 'save'): void
}>()

function close() {
  modelValue.value = false
}
</script>

<template>
  <Modal v-model="modelValue">
    <FieldModalForm
      v-model:field-name="fieldName"
      v-model:field-type="fieldType"
      v-model:field-settings="fieldSettings"
      v-model:field-public="fieldPublic"
      :is-edit="isEdit"
      :saving="saving"
      @save="emit('save')"
      @cancel="close"
    />
  </Modal>
</template>
