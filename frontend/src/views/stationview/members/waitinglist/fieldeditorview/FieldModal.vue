/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import Modal from '@/components/feedback/Modal.vue'
import FieldModalForm from './FieldModalForm.vue'

const modelValue = defineModel<boolean>({required: true})
const fieldName = defineModel<string>('fieldName', {required: true})
const fieldType = defineModel<string>('fieldType', {required: true})
const fieldRequired = defineModel<boolean>('fieldRequired', {required: true})
const fieldPublic = defineModel<boolean>('fieldPublic', {required: true})
const fieldEnumOptions = defineModel<string>('fieldEnumOptions', {required: true})

defineProps<{
  isEdit: boolean
  fieldTypes: readonly string[]
  fieldTypeLabel: (type: string) => string
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
      :is-edit="isEdit"
      v-model:field-name="fieldName"
      v-model:field-type="fieldType"
      v-model:field-required="fieldRequired"
      v-model:field-public="fieldPublic"
      v-model:field-enum-options="fieldEnumOptions"
      :field-types="fieldTypes"
      :field-type-label="fieldTypeLabel"
      :saving="saving"
      @save="emit('save')"
      @cancel="close"
    />
  </Modal>
</template>
