/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import {inventory} from '@/api'
import type {InventoryItem, InventorySize, StationMember} from '@/api/types'
import type {ItemLocationResponse} from '@/api/inventoryContainers'
import ItemEditForm from './ItemEditForm.vue'
import ItemMetadataDisplay from './ItemMetadataDisplay.vue'

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

function startEdit() {
  editName.value = props.item.name ?? ''
  editInternalId.value = props.item.internalId ?? ''
  editSizeId.value = props.item.sizeId != null ? String(props.item.sizeId) : ''
  editing.value = true
}

async function saveEdit() {
  try {
    const updated = await inventory.updateItem(props.item.id, {
      name: editName.value,
      internalId: editInternalId.value,
      sizeId: editSizeId.value ? Number(editSizeId.value) : undefined,
    })
    editing.value = false
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
      <SecondaryButton v-if="props.canEditItem && !editing" :icon="['fas', 'pen']" @click="startEdit">
        {{ t('itemDetail.edit') }}
      </SecondaryButton>
    </div>

    <ItemEditForm
      v-if="editing"
      v-model:name="editName"
      v-model:internalId="editInternalId"
      v-model:sizeId="editSizeId"
      :sizes="props.sizes"
      :save="saveEdit"
      @cancel="editing = false"
    />

    <ItemMetadataDisplay
      v-else
      :item="props.item"
      :sizes="props.sizes"
      :location="props.location"
      :assigned-member-identity="assignedMemberIdentity"
    />
  </NeutralContainer>
</template>
