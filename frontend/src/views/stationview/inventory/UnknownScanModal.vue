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
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import CustomFieldsSection from '@/views/stationview/inventory/detailview/CustomFieldsSection.vue'
import {serializeItemMetadata} from '@/views/stationview/inventory/detailview/itemMetadata'
import {inventory, inventoryFields} from '@/api'
import type {Inventory, InventoryItem, InventorySize} from '@/api/types'
import {InventoryTypes, ItemSource} from '@/api/types'
import type {InventoryFieldDefinition} from '@/api/inventoryFields'

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
const submitting = ref(false)

const targetInventoryId = ref<number | 'new'>('new')
const newInventoryName = ref('')
const newInventoryType = ref<'INTERNAL' | 'EXTERNAL' | 'MIXED'>(InventoryTypes.INTERNAL)
const newInventoryHasSizes = ref(false)
const newInventorySizes = ref<string[]>([''])

const itemName = ref('')
const itemSource = ref<'INTERNAL' | 'EXTERNAL'>(ItemSource.INTERNAL)

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
const showSourcePicker = computed(() => {
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
    if (inventories.value.length > 0) {
      targetInventoryId.value = inventories.value[0].id
    }
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? t('inventory.unknownScan.errors.loadFailed')
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
  if (inv.inventoryType === InventoryTypes.EXTERNAL) itemSource.value = ItemSource.EXTERNAL
  else if (inv.inventoryType === InventoryTypes.INTERNAL) itemSource.value = ItemSource.INTERNAL
  try {
    const [sizes, defs] = await Promise.all([
      inv.hasSizes ? inventory.listSizes(inv.id) : Promise.resolve([] as InventorySize[]),
      inventoryFields.listFields(inv.id).catch(() => [] as InventoryFieldDefinition[]),
    ])
    availableSizes.value = sizes
    fieldDefs.value = defs
  } catch {
    /* swallow — surfaced on submit */
  }
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

function buildMetadata(): string | undefined {
  if (fieldDefs.value.length === 0) return undefined
  const serialised = serializeItemMetadata(fieldDefs.value, fieldValues.value, false)
  const parsed = JSON.parse(serialised) as {owned: boolean; fields: Record<string, unknown>}
  return Object.keys(parsed.fields).length === 0 ? undefined : serialised
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
  return null
}

async function submit() {
  const validation = validate()
  if (validation) {
    error.value = validation
    return
  }
  submitting.value = true
  error.value = ''
  try {
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
      itemSource: itemSource.value,
      sizeId,
      metadata: buildMetadata(),
    })
    emit('created', item)
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? t('inventory.unknownScan.errors.createFailed')
  } finally {
    submitting.value = false
  }
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

    <Alert v-if="error" variant="error" class="mb-3">{{ error }}</Alert>

    <div v-if="loading" class="flex justify-center py-6">
      <Spinner size="md" />
    </div>
    <div v-else class="flex flex-col gap-3">
      <div class="space-y-1">
        <FieldLabel>{{ t('inventory.unknownScan.targetInventory') }}</FieldLabel>
        <SelectInput v-model="targetInventoryId">
          <option v-for="inv in sortedInventories" :key="inv.id" :value="inv.id">{{ inv.name }}</option>
          <option value="new">{{ t('inventory.unknownScan.createNewInventory') }}</option>
        </SelectInput>
      </div>

      <template v-if="isCreatingInventory">
        <div class="space-y-1">
          <FieldLabel>{{ t('inventory.unknownScan.newInventoryName') }}</FieldLabel>
          <TextInput v-model="newInventoryName" :placeholder="t('inventory.unknownScan.newInventoryPlaceholder')" />
        </div>
        <div class="space-y-1">
          <FieldLabel>{{ t('inventory.unknownScan.newInventoryType') }}</FieldLabel>
          <SelectInput v-model="newInventoryType">
            <option :value="InventoryTypes.INTERNAL">{{ t('inventory.unknownScan.types.INTERNAL') }}</option>
            <option :value="InventoryTypes.EXTERNAL">{{ t('inventory.unknownScan.types.EXTERNAL') }}</option>
            <option :value="InventoryTypes.MIXED">{{ t('inventory.unknownScan.types.MIXED') }}</option>
          </SelectInput>
        </div>
        <label class="flex items-center gap-2 text-sm">
          <ToggleInput v-model="newInventoryHasSizes" />
          <span>{{ t('inventory.unknownScan.newInventoryHasSizes') }}</span>
        </label>
        <div v-if="newInventoryHasSizes" class="space-y-1">
          <FieldLabel>{{ t('inventory.unknownScan.newInventorySizes') }}</FieldLabel>
          <p class="text-xs text-(--text-muted)">{{ t('inventory.unknownScan.newInventorySizesHint') }}</p>
          <div v-for="(_, idx) in newInventorySizes" :key="idx" class="flex items-center gap-2">
            <TextInput
                v-model="newInventorySizes[idx]"
                :placeholder="t('inventory.unknownScan.newInventorySizePlaceholder')"
                class="flex-1"
            />
            <IconButton
                v-if="newInventorySizes.length > 1"
                :icon="['fas', 'xmark']"
                :label="t('common.remove')"
                @click="removeNewSizeRow(idx)"
            />
          </div>
          <SecondaryButton size="sm" @click="addNewSizeRow">
            <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />
            {{ t('inventory.unknownScan.addSize') }}
          </SecondaryButton>
        </div>
      </template>

      <div class="space-y-1">
        <FieldLabel>{{ t('inventory.unknownScan.itemName') }}</FieldLabel>
        <TextInput v-model="itemName" :placeholder="t('inventory.unknownScan.itemNamePlaceholder')" />
      </div>

      <div v-if="effectiveHasSizes" class="space-y-1">
        <FieldLabel>
          {{ t('inventory.unknownScan.itemSize') }}
          <span class="text-error">*</span>
        </FieldLabel>
        <SelectInput v-model="pickedSizeLabel">
          <option value="">{{ t('inventory.unknownScan.pickSize') }}</option>
          <option v-for="label in sizeOptionLabels" :key="label" :value="label">{{ label }}</option>
        </SelectInput>
      </div>

      <div v-if="showSourcePicker" class="space-y-1">
        <FieldLabel>{{ t('inventory.unknownScan.itemSource') }}</FieldLabel>
        <SelectInput v-model="itemSource">
          <option :value="ItemSource.INTERNAL">{{ t('inventory.unknownScan.sources.INTERNAL') }}</option>
          <option :value="ItemSource.EXTERNAL">{{ t('inventory.unknownScan.sources.EXTERNAL') }}</option>
        </SelectInput>
      </div>

      <template v-if="fieldDefs.length > 0">
        <SubHeader class="pt-2">{{ t('inventory.fields.title') }}</SubHeader>
        <CustomFieldsSection :defs="fieldDefs" v-model="fieldValues" />
      </template>
    </div>

    <div class="flex justify-end gap-2 mt-4">
      <SecondaryButton @click="onClose">{{ t('common.cancel') }}</SecondaryButton>
      <PrimaryButton :disabled="submitting || loading" @click="submit">
        <font-awesome-icon :icon="['fas', 'plus']" class="mr-2" />
        {{ submitting ? t('common.saving') : t('inventory.unknownScan.create') }}
      </PrimaryButton>
    </div>
  </Modal>
</template>
