/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import type { WaitingList, WaitingListField } from '@/api/types'
import { waitingList } from '@/api'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import { useConfirmAction } from '@/composables/useConfirmAction'
import FieldsList from './fieldeditorview/FieldsList.vue'
import FieldModal from './fieldeditorview/FieldModal.vue'
import DeleteFieldModal from './fieldeditorview/DeleteFieldModal.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const listId = computed(() => Number(route.params.id))

const list = ref<WaitingList | null>(null)
const fields = ref<WaitingListField[]>([])

const showFieldModal = ref(false)
const editingField = ref<WaitingListField | null>(null)
const fieldName = ref('')
const fieldType = ref('TEXT')
const fieldRequired = ref(false)
const fieldPublic = ref(true)
const fieldEnumOptions = ref('')
const savingField = ref(false)

const fieldTypes = ['TEXT', 'NUMBER', 'DATE', 'BOOLEAN', 'ENUM'] as const

const fieldTypeLabels: Record<string, string> = {
  TEXT: t('waitingList.typeText'),
  NUMBER: t('waitingList.typeNumber'),
  DATE: t('waitingList.typeDate'),
  BOOLEAN: t('waitingList.typeBoolean'),
  ENUM: t('waitingList.typeEnum'),
}

function fieldTypeLabel(ft: string): string {
  return fieldTypeLabels[ft] ?? ft
}

const sortedFields = computed(() =>
  [...fields.value].sort((a, b) => a.position - b.position),
)

const {loading, error} = useAsyncLoader(async () => {
  const [listData, fieldData] = await Promise.all([
    waitingList.getById(listId.value),
    waitingList.listFields(listId.value),
  ])
  list.value = listData
  fields.value = fieldData
})

function openAddField() {
  editingField.value = null
  fieldName.value = ''
  fieldType.value = 'TEXT'
  fieldRequired.value = false
  fieldPublic.value = true
  fieldEnumOptions.value = ''
  showFieldModal.value = true
}

function openEditField(field: WaitingListField) {
  editingField.value = field
  fieldName.value = field.name
  fieldType.value = field.fieldType
  fieldRequired.value = field.required
  fieldPublic.value = field.isPublic ?? true
  const config = parseConfig(field.config)
  fieldEnumOptions.value = Array.isArray(config.options) ? config.options.join(', ') : ''
  showFieldModal.value = true
}

function parseConfig(configStr: string | undefined | null): Record<string, unknown> {
  if (!configStr) return {}
  try { return JSON.parse(configStr) } catch { return {} }
}

function buildConfig(): string {
  const config: Record<string, unknown> = {}
  if (fieldType.value === 'ENUM' && fieldEnumOptions.value.trim()) {
    config.options = fieldEnumOptions.value.split(',').map(o => o.trim()).filter(o => o)
  }
  return JSON.stringify(config)
}

async function saveField() {
  if (!fieldName.value.trim()) return
  savingField.value = true
  error.value = ''
  try {
    const data = {
      name: fieldName.value.trim(),
      fieldType: fieldType.value,
      config: buildConfig(),
      position: editingField.value?.position ?? fields.value.length,
      required: fieldRequired.value,
      isPublic: fieldPublic.value,
    }
    if (editingField.value) {
      await waitingList.updateField(listId.value, editingField.value.id, data)
    } else {
      await waitingList.createField(listId.value, data)
    }
    fields.value = await waitingList.listFields(listId.value)
    showFieldModal.value = false
  } catch {
    error.value = t('common.error')
  } finally {
    savingField.value = false
  }
}

const {
  show: showDeleteModal,
  target: deleteTarget,
  request: requestDelete,
  confirm: confirmDelete,
} = useConfirmAction<WaitingListField>({
  onConfirm: f => waitingList.deleteField(listId.value, f.id),
  onSuccess: async () => {
    fields.value = await waitingList.listFields(listId.value)
  },
  error,
})

async function moveField(index: number, direction: -1 | 1) {
  const sorted = sortedFields.value
  const targetIndex = index + direction
  if (targetIndex < 0 || targetIndex >= sorted.length) return
  error.value = ''
  try {
    const fieldA = sorted[index]
    const fieldB = sorted[targetIndex]
    await Promise.all([
      waitingList.updateField(listId.value, fieldA.id, {
        name: fieldA.name,
        fieldType: fieldA.fieldType,
        config: fieldA.config ?? '{}',
        position: fieldB.position,
        required: fieldA.required,
        isPublic: fieldA.isPublic,
      }),
      waitingList.updateField(listId.value, fieldB.id, {
        name: fieldB.name,
        fieldType: fieldB.fieldType,
        config: fieldB.config ?? '{}',
        position: fieldA.position,
        required: fieldB.required,
        isPublic: fieldB.isPublic,
      }),
    ])
    fields.value = await waitingList.listFields(listId.value)
  } catch {
    error.value = t('common.error')
  }
}

function goBack() {
  router.push({ name: 'waiting-list-detail', params: { id: listId.value } })
}

</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <SecondaryButton :icon="['fas', 'chevron-left']" @click="goBack">
        {{ t('waitingList.backToList') }}
      </SecondaryButton>

      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <FieldsList
        v-if="!loading && list"
        :list-name="list.name"
        :fields="fields"
        :field-type-label="fieldTypeLabel"
        :parse-config="parseConfig"
        @add="openAddField"
        @edit="openEditField"
        @delete="requestDelete"
        @move="moveField"
      />

      <FieldModal
        v-model="showFieldModal"
        :is-edit="!!editingField"
        v-model:field-name="fieldName"
        v-model:field-type="fieldType"
        v-model:field-required="fieldRequired"
        v-model:field-public="fieldPublic"
        v-model:field-enum-options="fieldEnumOptions"
        :field-types="fieldTypes"
        :field-type-label="fieldTypeLabel"
        :saving="savingField"
        @save="saveField"
      />

      <DeleteFieldModal
        v-model="showDeleteModal"
        :target="deleteTarget"
        @confirm="confirmDelete"
      />
    </div>
  </ViewContent>
</template>
