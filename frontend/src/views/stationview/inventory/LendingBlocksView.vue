/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import MutedText from '@/components/typography/MutedText.vue'
import AsyncSection from '@/components/feedback/AsyncSection.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import type {InventoryBlock} from '@/api/lending'
import * as lending from '@/api/lending'
import {inventory} from '@/api'
import type {Inventory, InventoryItem} from '@/api/types'
import {useSession} from '@/composables/useSession'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {formatDate} from '@/util/format'

const {t} = useI18n()
const router = useRouter()
const {loaded} = useSession()

const blocks = ref<InventoryBlock[]>([])
const inventories = ref<Inventory[]>([])
const itemsMap = ref<Map<number, InventoryItem[]>>(new Map())

interface BlockInventory {
  inventoryId: number
  inventoryName: string | null
  allItems: boolean
  items: { id: number; name: string | null; internalId: string | null }[]
}

interface GroupedBlock {
  key: string
  blockFrom: string
  blockTo: string
  reason: string
  isFullBlock: boolean
  inventories: BlockInventory[]
  blockIds: number[]
}

const groupedBlocks = computed<GroupedBlock[]>(() => {
  const groups = new Map<string, GroupedBlock>()

  for (const block of blocks.value) {
    // Group by same date range + reason
    const key = `${block.blockFrom}|${block.blockTo}|${block.reason}`

    if (!groups.has(key)) {
      groups.set(key, {
        key,
        blockFrom: block.blockFrom,
        blockTo: block.blockTo,
        reason: block.reason,
        isFullBlock: false,
        inventories: [],
        blockIds: [],
      })
    }

    const group = groups.get(key)!
    group.blockIds.push(block.id)

    if (!block.inventoryId) {
      // Full block (all inventories)
      group.isFullBlock = true
    } else if (block.itemId) {
      // Specific item — add to its inventory group
      let inv = group.inventories.find(i => i.inventoryId === block.inventoryId)
      if (!inv) {
        inv = {inventoryId: block.inventoryId, inventoryName: getInventoryName(block.inventoryId), allItems: false, items: []}
        group.inventories.push(inv)
      }
      const resolved = getItemName(block.inventoryId, block.itemId)
      inv.items.push({id: block.itemId, name: resolved.name, internalId: resolved.internalId})
    } else {
      // Entire inventory blocked
      let inv = group.inventories.find(i => i.inventoryId === block.inventoryId)
      if (!inv) {
        inv = {inventoryId: block.inventoryId, inventoryName: getInventoryName(block.inventoryId), allItems: true, items: []}
        group.inventories.push(inv)
      } else {
        inv.allItems = true
      }
    }
  }

  return [...groups.values()].sort((a, b) => a.blockFrom.localeCompare(b.blockFrom))
})

const {loading, error, reload: loadBlocks} = useAsyncLoader(async () => {
  if (!loaded.value) return
  const [b, invs] = await Promise.all([lending.listBlocks(), inventory.listInventories()])
  blocks.value = b
  inventories.value = invs

  const invIdsWithItems = new Set(b.filter(bl => bl.itemId && bl.inventoryId).map(bl => bl.inventoryId!))
  const itemResults = await Promise.all(
      [...invIdsWithItems].map(async id => ({id, items: await inventory.listItems(id)}))
  )
  itemsMap.value = new Map(itemResults.map(r => [r.id, r.items]))
}, {errorMessageKey: 'lending.loadError'})

function getInventoryName(id: number): string {
  return inventories.value.find(i => i.id === id)?.name ?? `#${id}`
}

function getItemName(invId: number, itemId: number): { name: string | null; internalId: string | null } {
  const items = itemsMap.value.get(invId)
  const item = items?.find(i => i.id === itemId)
  return {name: item?.name ?? null, internalId: item?.internalId ?? null}
}

watch(loaded, (v) => {
  if (v) loadBlocks()
})

async function handleDeleteGroup(group: GroupedBlock) {
  try {
    for (const id of group.blockIds) {
      await lending.deleteBlock(id)
    }
    await loadBlocks()
  } catch { /* ignore */ }
}

function itemLabel(item: { id: number; name: string | null; internalId: string | null }): string {
  if (item.name && item.internalId) return `${item.name} (${item.internalId})`
  return item.name || item.internalId || `#${item.id}`
}
</script>

<template>
  <ViewContent
      :title="t('pages.inventory-lending-blocks.title')"
      :subtitle="t('pages.inventory-lending-blocks.subtitle')"
  >
    <div class="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-2 mb-4">
      <div class="flex gap-2">
        <SecondaryButton :icon="['fas', 'chevron-left']" @click="router.push({name: 'inventory-lending'})">
          {{ t('lending.backToList') }}
        </SecondaryButton>
        <PrimaryButton :icon="['fas', 'plus']" @click="router.push({name: 'inventory-lending-blocks-create'})">
          {{ t('lending.addBlock') }}
        </PrimaryButton>
      </div>
    </div>

    <AsyncSection
        :empty="groupedBlocks.length === 0"
        :empty-message="t('lending.noBlocks')"
        :error="error"
        :loading="loading"
    >
      <div class="flex flex-col gap-2">
        <NeutralContainer v-for="group in groupedBlocks" :key="group.key">
          <div class="flex flex-col sm:flex-row sm:items-start justify-between gap-2">
            <div class="min-w-0 flex-1">
              <div class="flex items-center gap-2 flex-wrap">
                <span class="font-medium">{{ formatDate(group.blockFrom) }} – {{ formatDate(group.blockTo) }}</span>
                <PrimaryBadge v-if="group.isFullBlock">{{ t('lending.blockScopeAll') }}</PrimaryBadge>
              </div>

              <div v-if="group.inventories.length > 0" class="mt-2 space-y-1">
                <div v-for="inv in group.inventories" :key="inv.inventoryId" class="flex items-center gap-2 text-xs">
                  <SecondaryBadge>{{ inv.inventoryName || `#${inv.inventoryId}` }}</SecondaryBadge>
                  <MutedText v-if="inv.allItems" size="sm">{{ t('lending.blockItemAll') }}</MutedText>
                  <MutedText v-else size="sm">{{ inv.items.map(i => itemLabel(i)).join(', ') }}</MutedText>
                </div>
              </div>

              <MutedText v-if="group.reason" tag="div" size="sm" class="mt-1">{{ group.reason }}</MutedText>
            </div>
            <DeleteButton @click="handleDeleteGroup(group)"/>
          </div>
        </NeutralContainer>
      </div>
    </AsyncSection>
  </ViewContent>
</template>
