/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import ChecklistFormModal from './checklistmodals/ChecklistFormModal.vue'
import ChecklistColumnOrderEditor from './checklistmodals/ChecklistColumnOrderEditor.vue'
import type {ChecklistColumnDto} from '@/api/checklists'

const visible = defineModel<boolean>({required: true})

const props = defineProps<{
  initialName: string
  initialDescription: string
  initialColumns: ChecklistColumnDto[]
  saving: boolean
}>()

const emit = defineEmits<{
  (e: 'submit', payload: {name: string; description: string; orderedColumnIds: number[]}): void
}>()

const {t} = useI18n()

const name = ref(props.initialName)
const description = ref(props.initialDescription)
const orderedColumns = ref<ChecklistColumnDto[]>([...props.initialColumns])

watch(visible, (value) => {
  if (value) {
    name.value = props.initialName
    description.value = props.initialDescription
    orderedColumns.value = [...props.initialColumns]
  }
})

function submit() {
  const trimmed = name.value.trim()
  if (!trimmed) return
  emit('submit', {
    name: trimmed,
    description: description.value.trim(),
    orderedColumnIds: orderedColumns.value.map(c => c.id),
  })
}
</script>

<template>
  <ChecklistFormModal
      v-model="visible"
      v-model:name="name"
      v-model:description="description"
      :title="t('checklist.editChecklist')"
      size="md"
      :description-rows="3"
      :submit-disabled="saving || !name.trim()"
      @submit="submit"
  >
    <ChecklistColumnOrderEditor v-model="orderedColumns"/>
  </ChecklistFormModal>
</template>
