/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import {inventoryFields} from '@/api'
import {FieldType, defaultFieldConfig} from '@/api/inventoryFields'
import type {InventoryFieldDefinition} from '@/api/inventoryFields'
import FieldDraftEditor from './fielddefinitionssection/FieldDraftEditor.vue'
import type {DraftField} from './fielddefinitionssection/types'
import {useConfigPanel} from '@/composables/useConfigPanel'

const props = defineProps<{
  inventoryId: number
}>()

const {t} = useI18n()

const {config: fields, loading, error, reload: load} = useConfigPanel<InventoryFieldDefinition[]>({
  initial: [],
  fetch: () => inventoryFields.listFields(props.inventoryId),
  formatError: (e: any) => e?.response?.data?.message ?? t('inventory.fields.errors.loadFailed'),
})
const editing = ref<number | null>(null)
const draft = ref<DraftField | null>(null)
const submitting = ref(false)

const sortedFields = computed(() => [...fields.value].sort((a, b) => a.sortOrder - b.sortOrder || a.key.localeCompare(b.key)))

function newDraft(): DraftField {
  return {
    key: '',
    label: '',
    fieldType: FieldType.TEXT,
    required: false,
    sortOrder: sortedFields.value.length * 10,
    config: defaultFieldConfig(FieldType.TEXT),
  }
}

function startNew() {
  draft.value = newDraft()
  editing.value = -1
}

function startEdit(field: InventoryFieldDefinition) {
  draft.value = {
    id: field.id,
    key: field.key,
    label: field.label,
    fieldType: field.fieldType,
    required: field.required,
    sortOrder: field.sortOrder,
    config: field.config,
  }
  editing.value = field.id
}

function cancelEdit() {
  draft.value = null
  editing.value = null
}

async function save() {
  if (!draft.value) return
  submitting.value = true
  error.value = ''
  try {
    if (draft.value.id) {
      await inventoryFields.updateField(props.inventoryId, draft.value.id, {
        label: draft.value.label,
        required: draft.value.required,
        sortOrder: draft.value.sortOrder,
        config: draft.value.config,
      })
    } else {
      await inventoryFields.createField(props.inventoryId, {
        key: draft.value.key,
        label: draft.value.label,
        fieldType: draft.value.fieldType,
        required: draft.value.required,
        sortOrder: draft.value.sortOrder,
        config: draft.value.config,
      })
    }
    await load()
    cancelEdit()
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? t('inventory.fields.errors.saveFailed')
  } finally {
    submitting.value = false
  }
}

async function remove(field: InventoryFieldDefinition) {
  if (!confirm(t('inventory.fields.confirmDelete', {label: field.label}))) return
  try {
    await inventoryFields.deleteField(props.inventoryId, field.id)
    await load()
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? t('inventory.fields.errors.deleteFailed')
  }
}

watch(() => props.inventoryId, load)
</script>

<template>
  <NeutralContainer>
    <div class="flex items-center justify-between mb-2">
      <SectionHeader>{{ t('inventory.fields.title') }}</SectionHeader>
      <PrimaryButton v-if="!draft" size="sm" @click="startNew">
        <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />
        {{ t('inventory.fields.add') }}
      </PrimaryButton>
    </div>
    <p class="text-sm text-(--text-muted) mb-3">{{ t('inventory.fields.intro') }}</p>

    <Alert v-if="error" variant="error" class="mb-3">{{ error }}</Alert>

    <FieldDraftEditor
        v-if="draft"
        :draft="draft"
        :submitting="submitting"
        @cancel="cancelEdit"
        @save="save"
    />

    <div v-if="loading" class="py-4">
      <p class="text-sm text-(--text-muted)">{{ t('common.loading') }}</p>
    </div>
    <EmptyState v-else-if="sortedFields.length === 0" :message="t('inventory.fields.empty')" />
    <ul v-else class="divide-y divide-(--bg-accent)">
      <li v-for="f in sortedFields" :key="f.id" class="py-2 flex items-center gap-3">
        <span class="font-medium">{{ f.label }}</span>
        <span class="text-xs text-(--text-muted)">{{ f.key }}</span>
        <span class="text-xs text-(--text-muted)">{{ t(`inventory.fields.types.${f.fieldType}`) }}</span>
        <span v-if="f.required" class="text-xs text-error">{{ t('inventory.fields.required') }}</span>
        <div class="ml-auto flex gap-2">
          <IconButton :icon="['fas', 'pen']" :label="t('common.edit')" @click="startEdit(f)" />
          <DeleteButton :label="t('common.delete')" @click="remove(f)" />
        </div>
      </li>
    </ul>
  </NeutralContainer>
</template>
