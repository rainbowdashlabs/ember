/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import type {InventoryDetail, InventoryItem} from '@/api/inventory'
import type {StationMember} from '@/api/types'
import {inventory, stationMembers} from '@/api'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import SettingsSection from './editview/SettingsSection.vue'
import SizesSection from './editview/SizesSection.vue'
import ItemListSection from './editview/ItemListSection.vue'
import FieldDefinitionsSection from './editview/FieldDefinitionsSection.vue'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()

const inventoryId = computed(() => Number(route.params.id))

const detail = ref<InventoryDetail | null>(null)
const items = ref<InventoryItem[]>([])
const members = ref<StationMember[]>([])
const success = ref('')

const {loading, error} = useAsyncLoader(async () => {
  const [inv, allItems, allMembers] = await Promise.all([
    inventory.getInventory(inventoryId.value),
    inventory.listItems(inventoryId.value),
    stationMembers.listMembers(),
  ])
  detail.value = inv
  items.value = allItems
  members.value = allMembers
})

async function onSettingsSaved() {
  success.value = t('inventory.edit.settingsSaved')
  detail.value = await inventory.getInventory(inventoryId.value)
}

async function onSizesUpdated() {
  detail.value = await inventory.getInventory(inventoryId.value)
}

async function onItemsChanged() {
  items.value = await inventory.listItems(inventoryId.value)
}

function onError(message: string) {
  error.value = message
}
</script>

<template>
  <ViewContent
      :title="t('pages.inventory-edit.title')"
      :subtitle="t('pages.inventory-edit.subtitle')"
  >
    <div class="space-y-6">
      <SecondaryButton :icon="['fas', 'chevron-left']" @click="router.push({ name: 'inventory-manage' })">
        {{ t('inventory.edit.back') }}
      </SecondaryButton>

      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>
      <Alert v-if="success" variant="success">{{ success }}</Alert>

      <template v-if="!loading && detail">
        <SettingsSection :detail="detail" @saved="onSettingsSaved" @error="onError"/>

        <SizesSection v-if="detail.hasSizes" :inventory-id="inventoryId" :sizes="detail.sizes ?? []"
                      @updated="onSizesUpdated" @error="onError"/>

        <FieldDefinitionsSection :inventory-id="inventoryId"/>

        <ItemListSection :detail="detail" :items="items" :members="members"
                         @items-changed="onItemsChanged" @error="onError"/>
      </template>
    </div>
  </ViewContent>
</template>
