/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import Modal from '@/components/feedback/Modal.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import type { WaitingList, WaitingListField } from '@/api/types'
import { waitingList } from '@/api'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const listId = computed(() => Number(route.params.id))

const list = ref<WaitingList | null>(null)
const fields = ref<WaitingListField[]>([])
const loading = ref(true)
const error = ref('')

// Field modal
const showFieldModal = ref(false)
const editingField = ref<WaitingListField | null>(null)
const fieldName = ref('')
const fieldType = ref('TEXT')
const fieldRequired = ref(false)
const fieldEnumOptions = ref('')
const savingField = ref(false)

// Delete modal
const showDeleteModal = ref(false)
const deleteTarget = ref<WaitingListField | null>(null)
const deletingField = ref(false)

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
  [...fields.value].sort((a, b) => a.position - b.position)
)

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const [listData, fieldData] = await Promise.all([
      waitingList.getById(listId.value),
      waitingList.listFields(listId.value),
    ])
    list.value = listData
    fields.value = fieldData
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

function openAddField() {
  editingField.value = null
  fieldName.value = ''
  fieldType.value = 'TEXT'
  fieldRequired.value = false
  fieldEnumOptions.value = ''
  showFieldModal.value = true
}

function openEditField(field: WaitingListField) {
  editingField.value = field
  fieldName.value = field.name
  fieldType.value = field.fieldType
  fieldRequired.value = field.required
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

function requestDelete(field: WaitingListField) {
  deleteTarget.value = field
  showDeleteModal.value = true
}

async function confirmDelete() {
  if (!deleteTarget.value) return
  deletingField.value = true
  error.value = ''
  try {
    await waitingList.deleteField(listId.value, deleteTarget.value.id)
    fields.value = await waitingList.listFields(listId.value)
    showDeleteModal.value = false
    deleteTarget.value = null
  } catch {
    error.value = t('common.error')
  } finally {
    deletingField.value = false
  }
}

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
      }),
      waitingList.updateField(listId.value, fieldB.id, {
        name: fieldB.name,
        fieldType: fieldB.fieldType,
        config: fieldB.config ?? '{}',
        position: fieldA.position,
        required: fieldB.required,
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

onMounted(loadData)
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <SecondaryButton @click="goBack">
          <font-awesome-icon :icon="['fas', 'chevron-left']" class="mr-2" />
          {{ t('waitingList.backToList') }}
        </SecondaryButton>
      </div>

      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading && list">
        <NeutralContainer class="space-y-4">
          <div class="flex items-center justify-between">
            <SubHeader>{{ t('waitingList.fields') }} - {{ list.name }}</SubHeader>
            <PrimaryButton @click="openAddField">
              <font-awesome-icon :icon="['fas', 'plus']" class="mr-2" />
              {{ t('waitingList.addField') }}
            </PrimaryButton>
          </div>

          <p class="text-sm text-(--text-muted)">{{ t('waitingList.fieldsHint') }}</p>

          <EmptyState compact v-if="sortedFields.length === 0">{{ t('waitingList.noFields') }}</EmptyState>

          <div class="space-y-2">
            <div
              v-for="(field, index) in sortedFields"
              :key="field.id"
              class="flex items-center gap-3 rounded-lg px-4 py-3 bg-bg-light-accent/30 dark:bg-bg-dark-accent/30"
            >
              <div class="flex flex-col gap-1">
                <IconButton
                  icon="chevron-up"
                  :label="t('waitingList.moveUp')"
                  :disabled="index === 0"
                  @click="moveField(index, -1)"
                />
                <IconButton
                  icon="chevron-down"
                  :label="t('waitingList.moveDown')"
                  :disabled="index === sortedFields.length - 1"
                  @click="moveField(index, 1)"
                />
              </div>

              <div class="flex-1 min-w-0">
                <div class="flex items-center gap-2 flex-wrap">
                  <span class="font-medium">{{ field.name }}</span>
                  <span class="text-xs bg-secondary/15 text-secondary rounded-full px-2 py-0.5">{{ fieldTypeLabel(field.fieldType) }}</span>
                  <span v-if="field.required" class="text-xs bg-primary/15 text-primary rounded-full px-2 py-0.5">{{ t('waitingList.required') }}</span>
                </div>
                <div v-if="field.fieldType === 'ENUM'" class="text-xs text-(--text-muted) mt-1">
                  {{ t('waitingList.options') }}: {{ parseConfig(field.config).options ? (parseConfig(field.config).options as string[]).join(', ') : '-' }}
                </div>
              </div>

              <div class="flex items-center gap-1">
                <EditButton @click="openEditField(field)" />
                <DeleteButton @click="requestDelete(field)" />
              </div>
            </div>
          </div>
        </NeutralContainer>
      </template>

      <!-- Field modal -->
      <Modal v-model="showFieldModal">
        <div class="space-y-4">
          <SectionHeader>{{ editingField ? t('waitingList.editField') : t('waitingList.addField') }}</SectionHeader>
          <div class="space-y-1">
            <FieldLabel>{{ t('waitingList.fieldName') }}</FieldLabel>
            <TextInput v-model="fieldName" :placeholder="t('waitingList.fieldNamePlaceholder')" />
          </div>
          <div class="space-y-1">
            <FieldLabel>{{ t('waitingList.fieldType') }}</FieldLabel>
            <SelectInput v-model="fieldType">
              <option v-for="ft in fieldTypes" :key="ft" :value="ft">{{ fieldTypeLabel(ft) }}</option>
            </SelectInput>
          </div>
          <div v-if="fieldType === 'ENUM'" class="space-y-1">
            <FieldLabel>{{ t('waitingList.enumOptions') }}</FieldLabel>
            <TextInput v-model="fieldEnumOptions" :placeholder="t('waitingList.enumOptionsPlaceholder')" />
            <p class="text-xs text-(--text-muted)">{{ t('waitingList.enumOptionsHint') }}</p>
          </div>
          <div class="flex items-center gap-2">
            <ToggleInput v-model="fieldRequired" />
            <label class="text-sm font-medium">{{ t('waitingList.required') }}</label>
          </div>
          <div class="flex justify-end gap-2">
            <SecondaryButton @click="showFieldModal = false">{{ t('common.cancel') }}</SecondaryButton>
            <PrimaryButton :disabled="savingField || !fieldName.trim()" @click="saveField">
              {{ savingField ? t('common.loading') : t('common.save') }}
            </PrimaryButton>
          </div>
        </div>
      </Modal>

      <!-- Delete field modal -->
      <Modal v-model="showDeleteModal">
        <div class="space-y-4">
          <SectionHeader>{{ t('waitingList.deleteFieldTitle') }}</SectionHeader>
          <p class="text-sm">{{ t('waitingList.deleteFieldConfirm', { name: deleteTarget?.name }) }}</p>
          <div class="flex justify-end gap-2">
            <SecondaryButton @click="showDeleteModal = false">{{ t('common.cancel') }}</SecondaryButton>
            <ErrorButton :disabled="deletingField" @click="confirmDelete">
              {{ deletingField ? t('common.loading') : t('common.delete') }}
            </ErrorButton>
          </div>
        </div>
      </Modal>
    </div>
  </ViewContent>
</template>
