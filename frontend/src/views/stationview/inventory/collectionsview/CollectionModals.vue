/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import CollectionNameModal from './CollectionNameModal.vue'
import CollectionLineModal from './CollectionLineModal.vue'
import type {useCollectionEditing} from './useCollectionEditing'
import type {Inventory, InventoryItem} from '@/api/inventory'
import type {InventoryArt} from '@/api/inventoryArts'

/**
 * Every dialog the collections screen opens, kept together so the screen itself is the two panes
 * and nothing else.
 */
defineProps<{
  editing: ReturnType<typeof useCollectionEditing>
  inventories: Inventory[]
  items: InventoryItem[]
  arts: InventoryArt[]
}>()

const {t} = useI18n()
</script>

<template>
  <CollectionNameModal
      v-model:show="editing.showNameModal.value"
      v-model:name="editing.draftName.value"
      v-model:note="editing.draftNote.value"
      :creating="editing.creating.value"
      :save="editing.saveName"
  />
  <CollectionLineModal
      v-model:show="editing.showLineModal.value"
      v-model:kind="editing.lineKind.value"
      v-model:item-id="editing.lineItemId.value"
      v-model:art-id="editing.lineArtId.value"
      v-model:inventory-id="editing.lineInventoryId.value"
      v-model:quantity="editing.lineQuantity.value"
      :inventories="inventories"
      :items="items"
      :arts="arts"
      :saving="editing.saving.value"
      @submit="editing.addLine"
  />
  <ConfirmDeleteModal
      v-model="editing.showDeleteModal.value"
      :message="t('inventory.collections.deleteConfirm', {name: editing.resolved.value?.collection.name})"
      @confirm="editing.confirmDelete"
  />
</template>
