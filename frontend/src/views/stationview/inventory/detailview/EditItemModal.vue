/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ScanButton from '@/components/scanner/ScanButton.vue'
import {normaliseScannedPayload} from '@/components/scanner/useBarcodeScanner'
import type {InventoryItem, InventorySize} from '@/api/types'
import {inventory, inventoryContainers, inventoryFields} from '@/api'
import FieldValueInput from './FieldValueInput.vue'
import type {InventoryContainer} from '@/api/inventoryContainers'
import type {FieldTypeName, InventoryFieldDefinition} from '@/api/inventoryFields'
import {FieldType} from '@/api/inventoryFields'

const props = defineProps<{
  item: InventoryItem | null
  hasSizes: boolean
  sizes: InventorySize[]
}>()

const show = defineModel<boolean>({default: false})

const emit = defineEmits<{
  saved: []
}>()

const {t} = useI18n()

const itemName = ref('')
const internalId = ref('')
const sizeId = ref('')
const error = ref('')
const containerId = ref<number | null>(null)
const fieldDefs = ref<InventoryFieldDefinition[]>([])
const fieldValues = ref<Record<string, any>>({})
const containers = ref<InventoryContainer[]>([])
const owned = ref(false)

const sortedContainers = computed(() => [...containers.value].sort((a, b) => a.name.localeCompare(b.name)))
const sortedFields = computed(() =>
    [...fieldDefs.value].sort((a, b) => a.sortOrder - b.sortOrder || a.key.localeCompare(b.key)))

interface ParsedMetadata {
  owned: boolean
  fields: Record<string, {kind: FieldTypeName; value: unknown}>
}

function parseMetadata(raw: string | undefined): ParsedMetadata {
  if (!raw) return {owned: false, fields: {}}
  try {
    const parsed = JSON.parse(raw)
    return {
      owned: !!parsed?.owned,
      fields: parsed?.fields ?? {},
    }
  } catch {
    return {owned: false, fields: {}}
  }
}

function serializeMetadata(): string {
  const fields: Record<string, {kind: FieldTypeName; value: unknown}> = {}
  for (const def of fieldDefs.value) {
    const v = fieldValues.value[def.key]
    if (v === undefined || v === null || v === '') continue
    fields[def.key] = {kind: def.fieldType, value: v}
  }
  return JSON.stringify({owned: owned.value, fields})
}

async function loadForInventory(inventoryId: number) {
  try {
    const [defs, allContainers] = await Promise.all([
      inventoryFields.listFields(inventoryId),
      inventoryContainers.listContainers(),
    ])
    fieldDefs.value = defs
    containers.value = allContainers
  } catch {
    fieldDefs.value = []
    containers.value = []
  }
}

watch(() => props.item, async (item) => {
  if (!item) return
  itemName.value = item.name ?? ''
  internalId.value = item.internalId ?? ''
  sizeId.value = item.sizeId != null ? String(item.sizeId) : ''
  containerId.value = item.containerId ?? null
  const parsed = parseMetadata(item.metadata)
  owned.value = parsed.owned
  await loadForInventory(item.inventoryId)
  const values: Record<string, any> = {}
  for (const def of fieldDefs.value) {
    const stored = parsed.fields[def.key]
    values[def.key] = stored?.value ?? null
  }
  fieldValues.value = values
})

async function save() {
  if (!props.item) return
  error.value = ''
  try {
    const normalisedInternalId = internalId.value
        ? normaliseScannedPayload(internalId.value)
        : ''
    await inventory.updateItem(props.item.id, {
      name: itemName.value,
      internalId: normalisedInternalId || undefined,
      sizeId: sizeId.value ? Number(sizeId.value) : undefined,
      metadata: serializeMetadata(),
    })
    if (containerId.value !== (props.item.containerId ?? null)) {
      await inventoryContainers.setItemContainer(props.item.id, containerId.value)
    }
    show.value = false
    emit('saved')
  } catch (e) {
    error.value = t('common.error')
    throw e
  }
}
</script>

<template>
  <Modal v-model="show">
    <div class="space-y-4">
      <SectionHeader>{{ t('inventory.edit.editItem') }}</SectionHeader>
      <div class="space-y-1">
        <FieldLabel>{{ t('inventory.edit.itemName') }}</FieldLabel>
        <TextInput v-model="itemName" :placeholder="t('inventory.edit.itemNamePlaceholder')" />
      </div>
      <div class="space-y-1">
        <FieldLabel>{{ t('inventory.edit.itemInternalId') }}</FieldLabel>
        <div class="flex items-center gap-2">
          <TextInput v-model="internalId" class="flex-1" :placeholder="t('inventory.edit.itemInternalIdPlaceholder')" />
          <ScanButton @decoded="internalId = $event"/>
        </div>
      </div>
      <div v-if="props.hasSizes" class="space-y-1">
        <FieldLabel>{{ t('inventory.edit.itemSize') }}</FieldLabel>
        <SelectInput v-model="sizeId">
          <option value="">&#x2013;</option>
          <option v-for="size in props.sizes" :key="size.id" :value="String(size.id)">{{ size.label }}</option>
        </SelectInput>
      </div>
      <div class="space-y-1">
        <FieldLabel>{{ t('inventory.edit.itemLocation') }}</FieldLabel>
        <SelectInput v-model="containerId">
          <option :value="null">{{ t('inventory.edit.itemLocationNone') }}</option>
          <option v-for="c in sortedContainers" :key="c.id" :value="c.id">{{ c.name }}</option>
        </SelectInput>
      </div>
      <div v-if="sortedFields.length > 0" class="space-y-2 pt-2">
        <SubHeader>{{ t('inventory.fields.title') }}</SubHeader>
        <div v-for="def in sortedFields" :key="def.id" class="space-y-1">
          <FieldLabel>
            {{ def.label }}
            <span v-if="def.required" class="text-error">*</span>
          </FieldLabel>
          <FieldValueInput :field="def" v-model="fieldValues[def.key]" />
        </div>
      </div>
      <div class="flex justify-end gap-3">
        <SecondaryButton @click="show = false">{{ t('common.cancel') }}</SecondaryButton>
        <SaveButton :disabled="!itemName.trim()" :action="save"/>
      </div>
    </div>
  </Modal>
</template>
