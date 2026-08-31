/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import {inventory} from '@/api'
import type {Inventory, InventoryItem} from '@/api/inventory'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useSession} from '@/composables/useSession'
import {StationPermission} from '@/api/types'
import CollectionList from './collectionsview/CollectionList.vue'
import CollectionEditor from './collectionsview/CollectionEditor.vue'
import CollectionModals from './collectionsview/CollectionModals.vue'
import {useCollectionEditing} from './collectionsview/useCollectionEditing'

const {t} = useI18n()
const {hasPermission} = useSession()

const inventories = ref<Inventory[]>([])
const items = ref<InventoryItem[]>([])
const editable = computed(() => hasPermission(StationPermission.INVENTORY_EDIT))

const editing = useCollectionEditing()

const {loading, error} = useAsyncLoader(async () => {
  const [invs, allItems] = await Promise.all([inventory.listInventories(), inventory.listAllItems()])
  inventories.value = invs
  items.value = allItems
  await editing.reload()
})
</script>

<template>
  <ViewContent
      :title="t('pages.inventory-collections.title')"
      :subtitle="t('pages.inventory-collections.subtitle')"
  >
    <slot name="before"/>

    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error || editing.error.value" variant="error">{{ error || editing.error.value }}</Alert>

      <p class="text-sm text-(--text-muted)">{{ t('inventory.collections.hint') }}</p>

      <div v-if="!loading" class="grid gap-4 md:grid-cols-[18rem_1fr] items-start">
        <CollectionList
            :collections="editing.collections.value"
            :selected-id="editing.selectedId.value"
            :editable="editable"
            @select="editing.select"
            @create="editing.openCreate"
        />
        <CollectionEditor
            v-if="editing.resolved.value"
            v-model:date-from="editing.dateFrom.value"
            v-model:date-to="editing.dateTo.value"
            :resolved="editing.resolved.value"
            :editable="editable"
            @rename="editing.openRename"
            @remove="editing.askDelete"
            @add-line="editing.openAddLine"
            @update-quantity="editing.changeQuantity"
            @remove-line="editing.removeLine"
            @reorder="editing.reorder"
        />
      </div>

      <CollectionModals :editing="editing" :inventories="inventories" :items="items"/>
    </div>
  </ViewContent>
</template>
