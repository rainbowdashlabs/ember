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
import {inventory, inventoryArts, inventoryFields} from '@/api'
import type {InventoryItem} from '@/api/inventory'
import type {InventoryArt} from '@/api/inventoryArts'
import {defaultFieldConfig, FieldType, type InventoryFieldDefinition} from '@/api/inventoryFields'
import FieldDraftEditor from './fielddefinitionssection/FieldDraftEditor.vue'
import FieldRow from './fielddefinitionssection/FieldRow.vue'
import type {DraftField} from './fielddefinitionssection/types'
import {useConfigPanel} from '@/composables/useConfigPanel'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useConfirmDelete} from '@/composables/useConfirmDelete'
import {apiErrorMessage} from '@/util/apiError'
import {moveWithin} from '@/util/reorder'

const props = withDefaults(
    defineProps<{
      inventoryId: number
      /** Whether this inventory holds a drawer of different things, which is where kinds live. */
      heterogeneous?: boolean
    }>(),
    {heterogeneous: false},
)

const {t} = useI18n()

const {config: fields, loading, error, reload: load} = useConfigPanel<InventoryFieldDefinition[]>({
  initial: [],
  fetch: () => inventoryFields.listFields(props.inventoryId),
  formatError: (e) => apiErrorMessage(e) ?? t('inventory.fields.errors.loadFailed'),
})
const editing = ref<number | null>(null)
const draft = ref<DraftField | null>(null)
const arts = ref<InventoryArt[]>([])
const items = ref<InventoryItem[]>([])

/**
 * What a field can be written for besides the whole inventory. Loaded here rather than in the form
 * because both levels are read from the same inventory the fields belong to.
 */
async function loadLevels() {
  try {
    const [allArts, allItems] = await Promise.all([
      props.heterogeneous ? inventoryArts.listArts(props.inventoryId) : Promise.resolve([]),
      inventory.listItems(props.inventoryId),
    ])
    arts.value = allArts
    items.value = allItems
  } catch {
    arts.value = []
    items.value = []
  }
}

const sortedFields = computed(() => [...fields.value].sort((a, b) => a.sortOrder - b.sortOrder || a.key.localeCompare(b.key)))

function artName(artId: number): string {
  return arts.value.find(art => art.id === artId)?.name ?? String(artId)
}

function itemName(itemId: number): string {
  const item = items.value.find(candidate => candidate.id === itemId)
  return item?.name?.trim() || item?.internalId || String(itemId)
}

function newDraft(): DraftField {
  return {
    artId: null,
    itemId: null,
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
  loadLevels()
}

function startEdit(field: InventoryFieldDefinition) {
  draft.value = {
    id: field.id,
    artId: field.artId ?? null,
    itemId: field.itemId ?? null,
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
        artId: draft.value.artId,
        itemId: draft.value.itemId,
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
  persistOrder(moveWithin(sortedFields.value, fromIndex, toIndex))
}

watch(() => props.inventoryId, () => {
  load()
  loadLevels()
})
loadLevels()
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
        :arts="arts"
        :items="items"
        @cancel="cancelEdit"
        @save="save"
    />

    <div v-if="loading" class="py-4">
      <p class="text-sm text-(--text-muted)">{{ t('common.loading') }}</p>
    </div>
    <EmptyState v-else-if="sortedFields.length === 0" :message="t('inventory.fields.empty')" />
    <DragList v-else :items="sortedFields" :key-fn="(f) => f.id" @reorder="moveField">
      <template #default="{ item: f }">
        <FieldRow
            :field="f"
            :scope-label="f.itemId != null ? itemName(f.itemId) : f.artId != null ? artName(f.artId) : ''"
            @edit="startEdit(f)"
            @delete="requestDelete(f)"
        />
      </template>
    </DragList>

    <ConfirmDeleteModal
        v-model="showDeleteModal"
        :message="t('inventory.fields.confirmDelete', {label: deleteTarget?.label})"
        @confirm="confirmDelete"
    />
  </NeutralContainer>
</template>
