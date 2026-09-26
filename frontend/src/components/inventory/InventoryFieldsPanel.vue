/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import DragList from '@/components/input/DragList.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import {inventoryFields} from '@/api'
import {defaultFieldConfig, FieldType, type InventoryFieldDefinition} from '@/api/inventoryFields'
import FieldDraftEditor from './fields/FieldDraftEditor.vue'
import FieldRow from './fields/FieldRow.vue'
import type {DraftField} from './fields/types'
import {useConfigPanel} from '@/composables/useConfigPanel'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useConfirmDelete} from '@/composables/useConfirmDelete'
import {apiErrorMessage} from '@/util/apiError'
import {moveWithin} from '@/util/reorder'
import {describeFailure, type Failure} from '@/util/failure'

/**
 * The extra attributes of one thing: an inventory, one kind of thing in it, or one single piece.
 *
 * <p>The panel is mounted where the thing lives, and what it is mounted on settles what a new field
 * belongs to. That is why there is no scope to pick: choosing "kind" from a list while standing on
 * the inventory was exactly what made the fields of a kind impossible to find, and the place a
 * field is written down now says what it describes.
 *
 * <p>Each mount shows its own fields and no others. An overview of what hangs on the kinds and the
 * pieces would put the three levels back in one list, which is the mixing this replaces.
 */
const props = withDefaults(
    defineProps<{
      inventoryId: number
      /** The kind these fields describe, or null. At most one of the two is set. */
      artId?: number | null
      /** The single piece these fields describe, or null. */
      itemId?: number | null
    }>(),
    {artId: null, itemId: null},
)

const emit = defineEmits<{
  /** A field was written, changed or removed, so whatever renders the values has to read again. */
  changed: []
}>()

const {t} = useI18n()

const {config: fields, loading, failure: loadFailure, reload: load} = useConfigPanel<InventoryFieldDefinition[]>({
  initial: [],
  fetch: () => inventoryFields.listFields(props.inventoryId),
  formatError: (e) => apiErrorMessage(e) ?? t('inventory.fields.errors.loadFailed'),
})
const editing = ref<number | null>(null)
const draft = ref<DraftField | null>(null)

/** Only what belongs to the thing this panel is mounted on. */
const scopedFields = computed(() =>
    fields.value.filter(field => (field.artId ?? null) === props.artId && (field.itemId ?? null) === props.itemId),
)

const sortedFields = computed(() =>
    [...scopedFields.value].sort((a, b) => a.sortOrder - b.sortOrder || a.key.localeCompare(b.key)),
)

function newDraft(): DraftField {
  return {
    artId: props.artId,
    itemId: props.itemId,
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

/** What the reader's last action ran into, kept apart from the list never having arrived. */
const actionFailure = ref<Failure | null>(null)

const {running: submitting, run: save} = useAsyncAction(async () => {
  if (!draft.value) return
  actionFailure.value = null
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
    emit('changed')
    cancelEdit()
  } catch (e) {
    actionFailure.value = {...describeFailure(e, t), message: t('inventory.fields.errors.saveFailed')}
  }
})

const {
  show: showDeleteModal,
  target: deleteTarget,
  requestDelete,
  confirm: confirmDelete,
} = useConfirmDelete<InventoryFieldDefinition>({
  onDelete: field => inventoryFields.deleteField(props.inventoryId, field.id),
  onSuccess: () => {
    emit('changed')
    return load()
  },
  failure: actionFailure,
})

async function persistOrder(ordered: InventoryFieldDefinition[]) {
  actionFailure.value = null
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
    actionFailure.value = {...describeFailure(e, t), message: t('inventory.fields.errors.saveFailed')}
  }
}

function moveField(fromIndex: number, toIndex: number) {
  persistOrder(moveWithin(sortedFields.value, fromIndex, toIndex))
}

watch(() => [props.inventoryId, props.artId, props.itemId], () => {
  cancelEdit()
  load()
})
</script>

<template>
  <div data-testid="inventory-fields">
    <div class="flex items-center justify-between mb-2">
      <SectionHeader>{{ t('inventory.fields.title') }}</SectionHeader>
      <PrimaryButton v-if="!draft" compact data-testid="add-field" @click="startNew">
        <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />
        {{ t('inventory.fields.add') }}
      </PrimaryButton>
    </div>
    <p class="text-sm text-(--text-muted) mb-3">
      {{ props.itemId != null ? t('inventory.fields.introItem')
        : props.artId != null ? t('inventory.fields.introArt')
        : t('inventory.fields.intro') }}
    </p>

    <FailureAlert :failure="actionFailure ?? loadFailure" class="mb-3"/>

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
    <DragList v-else :items="sortedFields" :key-fn="(f) => f.id" @reorder="moveField">
      <template #default="{ item: f }">
        <FieldRow :field="f" @edit="startEdit(f)" @delete="requestDelete(f)" />
      </template>
    </DragList>

    <ConfirmDeleteModal
        v-model="showDeleteModal"
        :message="t('inventory.fields.confirmDelete', {label: deleteTarget?.label})"
        @confirm="confirmDelete"
    />
  </div>
</template>
