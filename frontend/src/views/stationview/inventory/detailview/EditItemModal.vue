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
import {normaliseScannedPayload} from '@/components/scanner/useBarcodeScanner'
import type {InventoryItem, InventorySize} from '@/api/types'
import {inventory, inventoryContainers, inventoryFields} from '@/api'
import EditItemFields from './edititemmodal/EditItemFields.vue'
import EditItemCustomFields from './edititemmodal/EditItemCustomFields.vue'
import EditItemFooter from './edititemmodal/EditItemFooter.vue'
import {parseItemMetadata, buildItemMetadata} from './itemMetadata'
import type {InventoryContainer} from '@/api/inventoryContainers'
import type {InventoryFieldDefinition} from '@/api/inventoryFields'

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
const fieldsInvalid = computed(() => inventoryFields.hasInvalidFieldValues(fieldDefs.value, fieldValues.value))

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
  const parsed = parseItemMetadata(item.metadata)
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
      metadata: buildItemMetadata(fieldDefs.value, fieldValues.value, owned.value),
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
      <SubHeader>{{ t('inventory.edit.editItem') }}</SubHeader>
      <EditItemFields
          v-model:itemName="itemName"
          v-model:internalId="internalId"
          v-model:sizeId="sizeId"
          v-model:containerId="containerId"
          :hasSizes="props.hasSizes"
          :sizes="props.sizes"
          :containers="sortedContainers"
      />
      <EditItemCustomFields :defs="fieldDefs" v-model="fieldValues"/>
      <EditItemFooter
          :saveDisabled="!itemName.trim() || fieldsInvalid"
          :save="save"
          @cancel="show = false"
      />
    </div>
  </Modal>
</template>
