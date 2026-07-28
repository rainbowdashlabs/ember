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
import Alert from '@/components/feedback/Alert.vue'
import DragList from '@/components/input/DragList.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import {inventoryFields} from '@/api'
import {FieldType, defaultFieldConfig} from '@/api/inventoryFields'
import type {InventoryFieldDefinition} from '@/api/inventoryFields'
import FieldDraftEditor from './fielddefinitionssection/FieldDraftEditor.vue'
import FieldRow from './fielddefinitionssection/FieldRow.vue'
import type {DraftField} from './fielddefinitionssection/types'
import {useConfigPanel} from '@/composables/useConfigPanel'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useConfirmDelete} from '@/composables/useConfirmDelete'
import {useBreakpoint} from '@/composables/useBreakpoint'
import {apiErrorMessage} from '@/util/apiError'

const props = defineProps<{
  inventoryId: number
}>()

const {t} = useI18n()
const {isMobile} = useBreakpoint()

const {config: fields, loading, error, reload: load} = useConfigPanel<InventoryFieldDefinition[]>({
  initial: [],
  fetch: () => inventoryFields.listFields(props.inventoryId),
  formatError: (e) => apiErrorMessage(e) ?? t('inventory.fields.errors.loadFailed'),
})
const editing = ref<number | null>(null)
const draft = ref<DraftField | null>(null)

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

const {running: submitting, run: save} = useAsyncAction(async () => {
  if (!draft.value) return
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
  } catch (e) {
    error.value = apiErrorMessage(e) ?? t('inventory.fields.errors.saveFailed')
  }
})

const {
  show: showDeleteModal,
  target: deleteTarget,
  requestDelete,
  confirm: confirmDelete,
} = useConfirmDelete<InventoryFieldDefinition>({
  onDelete: field => inventoryFields.deleteField(props.inventoryId, field.id),
  onSuccess: () => load(),
  error,
})

async function persistOrder(ordered: InventoryFieldDefinition[]) {
  error.value = ''
  try {
    for (const [i, f] of ordered.entries()) {
      if (f.sortOrder !== i * 10) {
        await inventoryFields.updateField(props.inventoryId, f.id, {
          label: f.label,
          required: f.required,
          sortOrder: i * 10,
          config: f.config,
        })
      }
    }
    await load()
  } catch (e) {
    error.value = apiErrorMessage(e) ?? t('inventory.fields.errors.saveFailed')
  }
}

function moveField(fromIndex: number, toIndex: number) {
  const ordered = [...sortedFields.value]
  const [moved] = ordered.splice(fromIndex, 1)
  if (!moved) return
  ordered.splice(toIndex, 0, moved)
  persistOrder(ordered)
}

watch(() => props.inventoryId, load)
</script>

<template>
  <NeutralContainer>
    <div class="flex items-center justify-between mb-2">
      <SectionHeader>{{ t('inventory.fields.title') }}</SectionHeader>
      <PrimaryButton v-if="!draft" compact @click="startNew">
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
    <ul v-else-if="isMobile" class="list-none">
      <li v-for="(f, i) in sortedFields" :key="f.id">
        <FieldRow
            :field="f"
            mode="arrows"
            :can-move-up="i > 0"
            :can-move-down="i < sortedFields.length - 1"
            @move-up="moveField(i, i - 1)"
            @move-down="moveField(i, i + 1)"
            @edit="startEdit(f)"
            @delete="requestDelete(f)"
        />
      </li>
    </ul>
    <DragList v-else :items="sortedFields" :key-fn="(f) => f.id" @reorder="moveField">
      <template #default="{ item: f }">
        <FieldRow :field="f" mode="drag" @edit="startEdit(f)" @delete="requestDelete(f)"/>
      </template>
    </DragList>

    <ConfirmDeleteModal
        v-model="showDeleteModal"
        :message="t('inventory.fields.confirmDelete', {label: deleteTarget?.label})"
        @confirm="confirmDelete"
    />
  </NeutralContainer>
</template>
