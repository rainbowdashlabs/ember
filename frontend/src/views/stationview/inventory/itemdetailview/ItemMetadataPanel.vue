/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import {inventory, inventoryArts} from '@/api'
import type {InventoryArt} from '@/api/inventoryArts'
import type {InventoryItem, InventorySize} from '@/api/inventory'
import type {StationMember} from '@/api/types'
import type {ItemLocationResponse} from '@/api/inventoryContainers'
import ItemEditForm from './ItemEditForm.vue'
import ItemMetadataDisplay from './ItemMetadataDisplay.vue'
import {parseItemMetadata} from '../detailview/itemMetadata'

const props = defineProps<{
  item: InventoryItem
  sizes: InventorySize[]
  members: StationMember[]
  location: ItemLocationResponse | null
  canEditItem: boolean
}>()

const emit = defineEmits<{
  updated: [item: InventoryItem]
  error: []
}>()

const {t} = useI18n()

const editing = ref(false)
const editName = ref('')
const editInternalId = ref('')
const editSizeId = ref('')
const editArtId = ref<number | null>(null)
const editArtDraft = ref('')
const arts = ref<InventoryArt[]>([])

/**
 * The kinds this piece's inventory holds. An empty list is the ordinary answer, because most
 * inventories hold one thing in many copies and have none, so nothing here insists on a kind.
 */
async function loadArts() {
  try {
    arts.value = await inventoryArts.listArts(props.item.inventoryId)
  } catch {
    arts.value = []
  }
}

watch(() => props.item.inventoryId, loadArts, {immediate: true})

const showArt = computed(() => arts.value.length > 0 || props.item.artId != null)

const artName = computed(() => arts.value.find(art => art.id === props.item.artId)?.name ?? null)

function startEdit() {
  editName.value = props.item.name ?? ''
  editInternalId.value = props.item.internalId ?? ''
  editSizeId.value = props.item.sizeId != null ? String(props.item.sizeId) : ''
  editArtId.value = props.item.artId ?? null
  editArtDraft.value = ''
  editing.value = true
}

async function saveEdit() {
  try {
    // A kind typed in reaches the server only now, together with the piece it was typed for.
    const resolvedArt = editArtDraft.value
        ? await inventoryArts.ensureArt(props.item.inventoryId, arts.value, editArtDraft.value)
        : editArtId.value
    const updated = await inventory.updateItem(props.item.id, {
      name: editName.value,
      internalId: editInternalId.value,
      sizeId: editSizeId.value ? Number(editSizeId.value) : undefined,
      artId: showArt.value ? resolvedArt : props.item.artId ?? null,
      metadata: parseItemMetadata(props.item.metadata),
    })
    editing.value = false
    if (editArtDraft.value) await loadArts()
    emit('updated', updated)
  } catch (e) {
    emit('error')
    throw e
  }
}

const assignedMemberIdentity = computed(() => {
  const id = props.item.assignedTo
  if (!id) return null
  return props.members.find(m => m.id === id)?.identity ?? null
})
</script>

<template>
  <NeutralContainer class="space-y-3">
    <div class="flex items-center justify-between">
      <SubHeader>{{ props.item.name }}</SubHeader>
      <SecondaryButton v-if="props.canEditItem && !editing" data-testid="item-edit"
                       :icon="['fas', 'pen']" @click="startEdit">
        {{ t('itemDetail.edit') }}
      </SecondaryButton>
    </div>

    <ItemEditForm
      v-if="editing"
      v-model:name="editName"
      v-model:internalId="editInternalId"
      v-model:sizeId="editSizeId"
      v-model:artId="editArtId"
      v-model:artDraft="editArtDraft"
      :sizes="props.sizes"
      :arts="arts"
      :show-art="showArt"
      :save="saveEdit"
      @cancel="editing = false"
    />

    <ItemMetadataDisplay
      v-else
      :item="props.item"
      :sizes="props.sizes"
      :art-name="artName"
      :location="props.location"
      :assigned-member-identity="assignedMemberIdentity"
    />
  </NeutralContainer>
</template>
