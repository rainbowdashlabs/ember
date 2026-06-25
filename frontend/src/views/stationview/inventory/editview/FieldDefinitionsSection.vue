/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import Alert from '@/components/feedback/Alert.vue'
import {inventoryFields} from '@/api'
import {
  FieldType,
  defaultFieldConfig,
} from '@/api/inventoryFields'
import type {
  FieldConfig,
  FieldTypeName,
  InventoryFieldDefinition,
} from '@/api/inventoryFields'

const props = defineProps<{
  inventoryId: number
}>()

const {t} = useI18n()

const fields = ref<InventoryFieldDefinition[]>([])
const loading = ref(true)
const error = ref('')
const editing = ref<number | null>(null)
const draft = ref<DraftField | null>(null)
const submitting = ref(false)

interface DraftField {
  id?: number
  key: string
  label: string
  fieldType: FieldTypeName
  required: boolean
  sortOrder: number
  config: FieldConfig
}

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

function onTypeChanged(value: FieldTypeName) {
  if (!draft.value) return
  if (draft.value.id) return
  draft.value.fieldType = value
  draft.value.config = defaultFieldConfig(value)
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

async function load() {
  loading.value = true
  error.value = ''
  try {
    fields.value = await inventoryFields.listFields(props.inventoryId)
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? t('inventory.fields.errors.loadFailed')
  } finally {
    loading.value = false
  }
}

watch(() => props.inventoryId, load)
onMounted(load)
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

    <div v-if="draft" class="mb-4 p-3 rounded-theme border border-(--bg-accent)">
      <SubHeader class="mb-2">
        {{ draft.id ? t('inventory.fields.edit') : t('inventory.fields.add') }}
      </SubHeader>
      <div class="grid grid-cols-1 md:grid-cols-2 gap-3">
        <label class="flex flex-col gap-1 text-sm">
          <span>{{ t('inventory.fields.label') }}</span>
          <TextInput v-model="draft.label" :placeholder="t('inventory.fields.labelPlaceholder')" />
        </label>
        <label class="flex flex-col gap-1 text-sm">
          <span>{{ t('inventory.fields.key') }}</span>
          <TextInput v-model="draft.key" :disabled="!!draft.id" :placeholder="t('inventory.fields.keyPlaceholder')" />
        </label>
        <label class="flex flex-col gap-1 text-sm">
          <span>{{ t('inventory.fields.type') }}</span>
          <SelectInput :model-value="draft.fieldType" :disabled="!!draft.id" @update:model-value="(v) => onTypeChanged(v as FieldTypeName)">
            <option v-for="v in Object.values(FieldType)" :key="v" :value="v">{{ t(`inventory.fields.types.${v}`) }}</option>
          </SelectInput>
        </label>
        <label class="flex flex-col gap-1 text-sm">
          <span>{{ t('inventory.fields.sortOrder') }}</span>
          <NumberInput v-model="draft.sortOrder" />
        </label>
        <label class="flex items-center gap-2 text-sm md:col-span-2">
          <ToggleInput v-model="draft.required" />
          <span>{{ t('inventory.fields.required') }}</span>
        </label>
      </div>

      <div v-if="draft.fieldType === FieldType.TEXT" class="grid grid-cols-1 md:grid-cols-2 gap-3 mt-3">
        <label class="flex items-center gap-2 text-sm">
          <ToggleInput v-model="(draft.config as any).multiline" />
          <span>{{ t('inventory.fields.text.multiline') }}</span>
        </label>
        <label class="flex flex-col gap-1 text-sm">
          <span>{{ t('inventory.fields.text.maxLength') }}</span>
          <NumberInput v-model="(draft.config as any).maxLength" />
        </label>
      </div>
      <div v-else-if="draft.fieldType === FieldType.NUMBER" class="grid grid-cols-1 md:grid-cols-4 gap-3 mt-3">
        <label class="flex flex-col gap-1 text-sm">
          <span>{{ t('inventory.fields.number.min') }}</span>
          <NumberInput v-model="(draft.config as any).min" />
        </label>
        <label class="flex flex-col gap-1 text-sm">
          <span>{{ t('inventory.fields.number.max') }}</span>
          <NumberInput v-model="(draft.config as any).max" />
        </label>
        <label class="flex flex-col gap-1 text-sm">
          <span>{{ t('inventory.fields.number.step') }}</span>
          <NumberInput v-model="(draft.config as any).step" />
        </label>
        <label class="flex flex-col gap-1 text-sm">
          <span>{{ t('inventory.fields.number.unit') }}</span>
          <TextInput v-model="(draft.config as any).unit" />
        </label>
      </div>
      <div v-else-if="draft.fieldType === FieldType.BOOLEAN" class="grid grid-cols-1 md:grid-cols-2 gap-3 mt-3">
        <label class="flex flex-col gap-1 text-sm">
          <span>{{ t('inventory.fields.boolean.trueLabel') }}</span>
          <TextInput v-model="(draft.config as any).trueLabel" />
        </label>
        <label class="flex flex-col gap-1 text-sm">
          <span>{{ t('inventory.fields.boolean.falseLabel') }}</span>
          <TextInput v-model="(draft.config as any).falseLabel" />
        </label>
      </div>
      <div v-else-if="draft.fieldType === FieldType.ENUM" class="mt-3">
        <SubHeader class="mb-2">{{ t('inventory.fields.enum.options') }}</SubHeader>
        <ul class="flex flex-col gap-2">
          <li v-for="(opt, idx) in (draft.config as any).options" :key="idx" class="flex items-center gap-2">
            <TextInput v-model="opt.value" :placeholder="t('inventory.fields.enum.value')" class="flex-1" />
            <TextInput v-model="opt.label" :placeholder="t('inventory.fields.enum.label')" class="flex-1" />
            <IconButton :icon="['fas', 'trash']" :label="t('common.delete')" @click="(draft.config as any).options.splice(idx, 1)" />
          </li>
        </ul>
        <SecondaryButton size="sm" class="mt-2" @click="(draft.config as any).options.push({value: '', label: ''})">
          <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />
          {{ t('inventory.fields.enum.add') }}
        </SecondaryButton>
      </div>

      <div class="flex justify-end gap-2 mt-3">
        <SecondaryButton @click="cancelEdit">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="submitting" @click="save">
          {{ submitting ? t('common.saving') : t('common.save') }}
        </PrimaryButton>
      </div>
    </div>

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
