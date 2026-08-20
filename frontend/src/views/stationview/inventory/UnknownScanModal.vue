/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import UnknownScanForm from '@/views/stationview/inventory/unknownscanmodal/UnknownScanForm.vue'
import {buildItemMetadata} from '@/views/stationview/inventory/detailview/itemMetadata'
import {InventoryTypes, ItemOwner, type Inventory, type InventoryItem, type InventorySize, type ItemMetadata, type ItemOwnerName} from '@/api/inventory'
import {inventory, inventoryFields} from '@/api'
import type {InventoryFieldDefinition} from '@/api/inventoryFields'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {apiErrorMessage} from '@/util/apiError'

const props = defineProps<{
  scannedCode: string
  context: 'container' | 'member'
}>()

const emit = defineEmits<{
  created: [item: InventoryItem]
  close: []
}>()

const {t} = useI18n()

const open = ref(true)
const inventories = ref<Inventory[]>([])
const loading = ref(true)
const error = ref('')

const targetInventoryId = ref<number | 'new'>('new')
const newInventoryName = ref('')
const newInventoryType = ref<'INTERNAL' | 'EXTERNAL' | 'MIXED'>(InventoryTypes.INTERNAL)
const newInventoryHasSizes = ref(false)
const newInventorySizes = ref<string[]>([''])

const itemName = ref('')
const ownerKind = ref<ItemOwnerName>(ItemOwner.STATION)

const availableSizes = ref<InventorySize[]>([])
const pickedSizeLabel = ref<string>('')
const fieldDefs = ref<InventoryFieldDefinition[]>([])
const fieldValues = ref<Record<string, unknown>>({})

const sortedInventories = computed(() => [...inventories.value].sort((a, b) =>
    (a.name ?? '').localeCompare(b.name ?? '')))

const selectedInventory = computed(() =>
    typeof targetInventoryId.value === 'number'
        ? inventories.value.find(i => i.id === targetInventoryId.value)
        : null)

const isCreatingInventory = computed(() => targetInventoryId.value === 'new')
const effectiveHasSizes = computed(() =>
    isCreatingInventory.value ? newInventoryHasSizes.value : !!selectedInventory.value?.hasSizes)
const cleanedNewSizes = computed(() => newInventorySizes.value.map(s => s.trim()).filter(s => s.length > 0))
const sizeOptionLabels = computed(() => {
  if (isCreatingInventory.value) return cleanedNewSizes.value
  return availableSizes.value.map(s => s.label).filter((l): l is string => !!l)
})
const showOwnerPicker = computed(() => {
  if (isCreatingInventory.value) return newInventoryType.value === InventoryTypes.MIXED
  return selectedInventory.value?.inventoryType === InventoryTypes.MIXED
})

const sortedFieldDefs = computed(() =>
    [...fieldDefs.value].sort((a, b) => a.sortOrder - b.sortOrder || a.key.localeCompare(b.key)))

async function load() {
  loading.value = true
  error.value = ''
  try {
    inventories.value = await inventory.listInventories()
    const first = inventories.value[0]
    if (first) {
      targetInventoryId.value = first.id
    }
  } catch (e) {
    error.value = apiErrorMessage(e) ?? t('inventory.unknownScan.errors.loadFailed')
  } finally {
    loading.value = false
  }
}

watch(selectedInventory, async (inv) => {
  pickedSizeLabel.value = ''
  fieldValues.value = {}
  availableSizes.value = []
  fieldDefs.value = []
  if (!inv) return
  itemName.value = inv.name ?? ''
  if (inv.inventoryType === InventoryTypes.EXTERNAL) ownerKind.value = ItemOwner.CLUSTER
  else if (inv.inventoryType === InventoryTypes.INTERNAL) ownerKind.value = ItemOwner.STATION
  try {
    const [sizes, defs] = await Promise.all([
      inv.hasSizes ? inventory.listSizes(inv.id) : Promise.resolve([] as InventorySize[]),
      inventoryFields.listFields(inv.id).catch(() => [] as InventoryFieldDefinition[]),
    ])
    availableSizes.value = sizes
    fieldDefs.value = defs
  } catch {
    /* swallow - surfaced on submit */
  }
})

watch(newInventoryName, (name) => {
  if (isCreatingInventory.value) itemName.value = name
})

watch(targetInventoryId, () => {
  pickedSizeLabel.value = ''
  if (!isCreatingInventory.value) {
    newInventoryName.value = ''
    newInventoryHasSizes.value = false
    newInventorySizes.value = ['']
  }
})

watch(newInventoryHasSizes, (on) => {
  if (!on) {
    newInventorySizes.value = ['']
    pickedSizeLabel.value = ''
  }
})

function addNewSizeRow() {
  newInventorySizes.value.push('')
}

function removeNewSizeRow(index: number) {
  newInventorySizes.value.splice(index, 1)
  if (newInventorySizes.value.length === 0) newInventorySizes.value.push('')
}

function buildMetadata(): ItemMetadata | undefined {
  if (fieldDefs.value.length === 0) return undefined
  const built = buildItemMetadata(fieldDefs.value, fieldValues.value)
  return Object.keys(built.fields).length === 0 ? undefined : built
}

function validate(): string | null {
  if (!itemName.value.trim()) return t('inventory.unknownScan.errors.nameRequired')
  if (isCreatingInventory.value) {
    if (!newInventoryName.value.trim()) return t('inventory.unknownScan.errors.inventoryNameRequired')
    if (newInventoryHasSizes.value) {
      if (cleanedNewSizes.value.length === 0) return t('inventory.unknownScan.errors.sizesRequired')
      const seen = new Set<string>()
      for (const label of cleanedNewSizes.value) {
        const norm = label.toLowerCase()
        if (seen.has(norm)) return t('inventory.unknownScan.errors.sizesDuplicate', {label})
        seen.add(norm)
      }
    }
  }
  if (effectiveHasSizes.value && !pickedSizeLabel.value) {
    return t('inventory.unknownScan.errors.sizeRequired')
  }
  for (const def of sortedFieldDefs.value) {
    if (!def.required) continue
    const v = fieldValues.value[def.key]
    if (v === undefined || v === null || v === '') {
      return t('inventory.unknownScan.errors.fieldRequired', {label: def.label})
    }
  }
  for (const def of sortedFieldDefs.value) {
    if (inventoryFields.numberFieldViolation(def, fieldValues.value[def.key])) {
      return t('inventory.fields.numberInvalid', {label: def.label})
    }
  }
  return null
}

const {running: submitting, error: submitError, run: runSubmit} = useAsyncAction(async () => {
  let inventoryId: number
  let sizes: InventorySize[]
  if (isCreatingInventory.value) {
    const created = await inventory.createInventory({
      name: newInventoryName.value.trim(),
      inventoryType: newInventoryType.value,
      hasSizes: newInventoryHasSizes.value,
    })
    inventoryId = created.id
    if (newInventoryHasSizes.value) {
      for (let i = 0; i < cleanedNewSizes.value.length; i++) {
        await inventory.createSize(inventoryId, {label: cleanedNewSizes.value[i], position: i, note: ''})
      }
      sizes = await inventory.listSizes(inventoryId)
    } else {
      sizes = []
    }
  } else {
    inventoryId = targetInventoryId.value as number
    sizes = availableSizes.value
  }
  let sizeId: number | undefined
  if (effectiveHasSizes.value) {
    const match = sizes.find(s => s.label === pickedSizeLabel.value)
    if (!match) {
      error.value = t('inventory.unknownScan.errors.sizeRequired')
      return
    }
    sizeId = match.id
  }
  const item = await inventory.createItem(inventoryId, {
    internalId: props.scannedCode,
    name: itemName.value.trim(),
    ownerKind: ownerKind.value,
    sizeId,
    metadata: buildMetadata(),
  })
  emit('created', item)
}, {formatError: (e) => apiErrorMessage(e) ?? t('inventory.unknownScan.errors.createFailed')})

async function submit() {
  const validation = validate()
  if (validation) {
    error.value = validation
    return
  }
  error.value = ''
  await runSubmit()
}

function onClose() {
  open.value = false
  emit('close')
}

load()
</script>

<template>
  <Modal v-model="open" size="md" @update:modelValue="(v) => { if (!v) onClose() }">
    <SubHeader class="mb-2">{{ t('inventory.unknownScan.title') }}</SubHeader>
    <p class="text-sm text-(--text-muted) mb-3">
      {{ context === 'member' ? t('inventory.unknownScan.introMember') : t('inventory.unknownScan.introContainer') }}
    </p>
    <p class="text-sm mb-3">
      <span class="text-(--text-muted)">{{ t('inventory.unknownScan.code') }}:</span>
      <code class="ml-2 font-mono">{{ scannedCode }}</code>
    </p>

    <Alert v-if="error || submitError" variant="error" class="mb-3">{{ error || submitError }}</Alert>

    <div v-if="loading" class="flex justify-center py-6">
      <Spinner size="md" />
    </div>
    <UnknownScanForm
        v-else
        v-model:targetInventoryId="targetInventoryId"
        v-model:newInventoryName="newInventoryName"
        v-model:newInventoryType="newInventoryType"
        v-model:newInventoryHasSizes="newInventoryHasSizes"
        v-model:newInventorySizes="newInventorySizes"
        v-model:itemName="itemName"
        v-model:pickedSizeLabel="pickedSizeLabel"
        v-model:ownerKind="ownerKind"
        v-model:fieldValues="fieldValues"
        :sortedInventories="sortedInventories"
        :isCreatingInventory="isCreatingInventory"
        :effectiveHasSizes="effectiveHasSizes"
        :sizeOptionLabels="sizeOptionLabels"
        :showOwnerPicker="showOwnerPicker"
        :fieldDefs="fieldDefs"
        @addNewSize="addNewSizeRow"
        @removeNewSize="removeNewSizeRow"
    />

    <div class="flex justify-end gap-2 mt-4">
      <SecondaryButton @click="onClose">{{ t('common.cancel') }}</SecondaryButton>
      <PrimaryButton :disabled="submitting || loading" @click="submit">
        <font-awesome-icon :icon="['fas', 'plus']" class="mr-2" />
        {{ submitting ? t('common.saving') : t('inventory.unknownScan.create') }}
      </PrimaryButton>
    </div>
  </Modal>
</template>
