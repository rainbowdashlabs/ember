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
import type {InventoryItem, InventorySize} from '@/api/inventory'
import {inventory, inventoryArts, inventoryContainers, inventoryFields, inventoryTags} from '@/api'
import type {InventoryArt} from '@/api/inventoryArts'
import type {InventoryTag} from '@/api/inventoryTags'
import EditItemFields from './edititemmodal/EditItemFields.vue'
import EditItemCustomFields from './edititemmodal/EditItemCustomFields.vue'
import EditItemFooter from './edititemmodal/EditItemFooter.vue'
import InventoryFieldsPanel from '@/components/inventory/InventoryFieldsPanel.vue'
import {parseItemMetadata, buildItemMetadata} from './itemMetadata'
import type {InventoryContainer} from '@/api/inventoryContainers'
import type {InventoryFieldDefinition} from '@/api/inventoryFields'

const props = withDefaults(
    defineProps<{
      item: InventoryItem | null
      hasSizes: boolean
      sizes: InventorySize[]
      /** Whether this inventory holds a drawer of different things, which is where kinds live. */
      heterogeneous?: boolean
    }>(),
    {heterogeneous: false},
)

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
const artId = ref<number | null>(null)
const artDraft = ref('')
const arts = ref<InventoryArt[]>([])
const tags = ref<InventoryTag[]>([])
const tagNames = ref<string[]>([])
const fieldDefs = ref<InventoryFieldDefinition[]>([])
const fieldValues = ref<Record<string, any>>({})
const containers = ref<InventoryContainer[]>([])

const sortedContainers = computed(() => [...containers.value].sort((a, b) => a.name.localeCompare(b.name)))
const fieldsInvalid = computed(() => inventoryFields.hasInvalidFieldValues(fieldDefs.value, fieldValues.value))

/**
 * The fields this one piece carries, which the backend works out from all three levels.
 *
 * Anything the piece recorded under a key that is not in this list belonged to a kind it no longer
 * has. It stays where it is and simply does not appear, which is why the form never sees it and
 * never sends it back as an empty value.
 */
async function loadForItem(item: InventoryItem) {
  try {
    const [defs, allContainers, allArts, allTags, worn] = await Promise.all([
      inventoryFields.listItemFields(item.id),
      inventoryContainers.listContainers(),
      props.heterogeneous ? inventoryArts.listArts(item.inventoryId) : Promise.resolve([]),
      inventoryTags.listTags(),
      inventoryTags.itemTags(item.id),
    ])
    fieldDefs.value = defs
    containers.value = allContainers
    arts.value = allArts
    tags.value = allTags
    tagNames.value = worn.map(tag => tag.name)
  } catch {
    fieldDefs.value = []
    containers.value = []
    arts.value = []
    tags.value = []
    tagNames.value = []
  }
}

/**
 * Reads the fields this piece carries again and lays the values out along them.
 *
 * <p>Anything already typed into the open form is kept, so writing down a new field beside the form
 * does not throw away what somebody was in the middle of entering.
 */
async function reloadFields() {
  const item = props.item
  if (!item) return
  const typed = fieldValues.value
  await loadForItem(item)
  const parsed = parseItemMetadata(item.metadata)
  const values: Record<string, any> = {}
  for (const def of fieldDefs.value) {
    values[def.key] = def.key in typed ? typed[def.key] : parsed.fields[def.key]?.value ?? null
  }
  fieldValues.value = values
}

watch(() => props.item, async (item) => {
  if (!item) return
  itemName.value = item.name ?? ''
  internalId.value = item.internalId ?? ''
  sizeId.value = item.sizeId != null ? String(item.sizeId) : ''
  containerId.value = item.containerId ?? null
  artId.value = item.artId ?? null
  artDraft.value = ''
  fieldValues.value = {}
  await reloadFields()
})

async function save() {
  if (!props.item) return
  error.value = ''
  try {
    const normalisedInternalId = internalId.value
        ? normaliseScannedPayload(internalId.value)
        : ''
    // The kind typed into the picker is only written down now, so a form that was abandoned
    // instead of saved leaves nothing behind.
    const resolvedArt = props.heterogeneous
        ? artDraft.value
            ? await inventoryArts.ensureArt(props.item.inventoryId, arts.value, artDraft.value)
            : artId.value
        : null
    await inventory.updateItem(props.item.id, {
      name: itemName.value,
      internalId: normalisedInternalId || undefined,
      sizeId: sizeId.value ? Number(sizeId.value) : undefined,
      artId: resolvedArt,
      metadata: buildItemMetadata(fieldDefs.value, fieldValues.value),
    })
    if (containerId.value !== (props.item.containerId ?? null)) {
      await inventoryContainers.setItemContainer(props.item.id, containerId.value)
    }
    await inventoryTags.setItemTags(props.item.id, tagNames.value)
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
          v-model:artId="artId"
          v-model:artDraft="artDraft"
          v-model:tagNames="tagNames"
          :hasSizes="props.hasSizes"
          :sizes="props.sizes"
          :containers="sortedContainers"
          :arts="arts"
          :showArt="props.heterogeneous"
          :tags="tags"
      />
      <EditItemCustomFields :defs="fieldDefs" v-model="fieldValues"/>
      <template v-if="props.item">
        <hr class="border-(--bg-accent)">
        <InventoryFieldsPanel
            :inventory-id="props.item.inventoryId"
            :item-id="props.item.id"
            @changed="reloadFields"
        />
      </template>
      <EditItemFooter
          :saveDisabled="!itemName.trim() || fieldsInvalid"
          :save="save"
          @cancel="show = false"
      />
    </div>
  </Modal>
</template>
