/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, watchEffect} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import {checklists} from '@/api'
import type {ChecklistColumnDto} from '@/api/types'

const props = defineProps<{
  checklistId: number
  column?: ChecklistColumnDto
  totalColumns: number
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'changed'): void
}>()

const {t} = useI18n()

const label = ref('')
const description = ref('')
const position = ref(0)
const saving = ref(false)
const deleting = ref(false)
const checkedCount = ref(0)
const showDeleteConfirm = ref(false)
const error = ref('')

watchEffect(() => {
  if (props.column) {
    label.value = props.column.label
    description.value = props.column.description
    position.value = props.column.position
  } else {
    label.value = ''
    description.value = ''
    position.value = props.totalColumns
  }
})

async function save() {
  if (!label.value.trim()) return
  saving.value = true
  error.value = ''
  try {
    if (props.column) {
      await checklists.updateColumn(props.checklistId, props.column.id, {
        label: label.value.trim(),
        description: description.value.trim(),
        position: position.value,
      })
    } else {
      await checklists.addColumn(props.checklistId, {
        label: label.value.trim(),
        description: description.value.trim(),
      })
    }
    emit('changed')
  } catch {
    error.value = t('checklist.savingError')
  }
  saving.value = false
}

async function askDelete() {
  if (!props.column) return
  showDeleteConfirm.value = true
  checkedCount.value = 0
  try {
    const detail = await checklists.getChecklist(props.checklistId)
    const cells = detail.cells.filter(c => c.columnId === props.column?.id && c.checked)
    checkedCount.value = cells.length
  } catch {
    checkedCount.value = 0
  }
}

async function applyDelete() {
  if (!props.column) return
  deleting.value = true
  try {
    await checklists.deleteColumn(props.checklistId, props.column.id)
    showDeleteConfirm.value = false
    emit('changed')
  } finally {
    deleting.value = false
  }
}
</script>

<template>
  <Modal :model-value="true" size="md" @update:model-value="emit('close')">
    <div class="space-y-3">
      <div class="font-semibold">{{ column ? t('checklist.editColumn') : t('checklist.addColumn') }}</div>
      <Alert v-if="error" variant="error">{{ error }}</Alert>
      <div>
        <FieldLabel>{{ t('checklist.columnLabel') }}</FieldLabel>
        <TextInput v-model="label" :placeholder="t('checklist.columnLabelPlaceholder')"/>
      </div>
      <div>
        <FieldLabel>{{ t('checklist.columnDescription') }}</FieldLabel>
        <TextAreaInput v-model="description" :placeholder="t('checklist.columnDescriptionPlaceholder')" rows="2"/>
      </div>
      <div v-if="column">
        <FieldLabel>{{ t('checklist.editColumnHint') }}</FieldLabel>
        <NumberInput v-model="position" :min="0" :max="totalColumns - 1"/>
      </div>
      <div class="flex justify-between gap-2 pt-2">
        <DeleteButton v-if="column" @click="askDelete">
          <font-awesome-icon :icon="['fas', 'trash']" class="mr-1"/>
          {{ t('checklist.deleteColumnTitle') }}
        </DeleteButton>
        <div class="flex gap-2 ml-auto">
          <SecondaryButton @click="emit('close')">{{ t('checklist.cancel') }}</SecondaryButton>
          <PrimaryButton :disabled="saving || !label.trim()" @click="save">{{ t('checklist.save') }}</PrimaryButton>
        </div>
      </div>
    </div>
  </Modal>

  <Modal v-if="column" v-model="showDeleteConfirm" size="sm">
    <div class="space-y-3">
      <div class="font-semibold">{{ t('checklist.deleteColumnTitle') }}</div>
      <p>{{ t('checklist.deleteColumnMessage', {name: column.label, count: checkedCount}) }}</p>
      <div class="flex justify-end gap-2">
        <SecondaryButton @click="showDeleteConfirm = false">{{ t('checklist.cancel') }}</SecondaryButton>
        <DeleteButton :disabled="deleting" @click="applyDelete">{{ t('checklist.deleteColumnTitle') }}</DeleteButton>
      </div>
    </div>
  </Modal>
</template>
