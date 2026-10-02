/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useInventoryRoutes} from '@/composables/useInventoryRoutes'
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import AsyncSection from '@/components/feedback/AsyncSection.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import LendingBlockGroupTile from './lendingblocksview/LendingBlockGroupTile.vue'
import type {GroupedBlock} from './lendingblocksview/types'
import * as lending from '@/api/lending'
import {inventory} from '@/api'
import type {Inventory, InventoryBlock, InventoryItem} from '@/api/generated/schema'
import {useSession} from '@/composables/useSession'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {describeFailure} from '@/util/failure'

const routes = useInventoryRoutes()

const {t} = useI18n()
const router = useRouter()
const {loaded} = useSession()

const blocks = ref<InventoryBlock[]>([])
const inventories = ref<Inventory[]>([])
const itemsMap = ref<Map<number, InventoryItem[]>>(new Map())

/**
 * Blocks sharing a date range and a reason, shown as one. A block without an inventory covers all
 * of them, one with a piece names that piece, and the rest cover their whole inventory.
 */
const groupedBlocks = computed<GroupedBlock[]>(() => {
  const groups = new Map<string, GroupedBlock>()

  for (const block of blocks.value) {
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
      group.isFullBlock = true
    } else if (block.itemId) {
      let inv = group.inventories.find(i => i.inventoryId === block.inventoryId)
      if (!inv) {
        inv = {inventoryId: block.inventoryId, inventoryName: getInventoryName(block.inventoryId), allItems: false, items: []}
        group.inventories.push(inv)
      }
      const resolved = getItemName(block.inventoryId, block.itemId)
      inv.items.push({id: block.itemId, name: resolved.name, internalId: resolved.internalId})
    } else {
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

const {loading, failure, reload: loadBlocks} = useAsyncLoader(async () => {
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

/**
 * Lifts a whole block, one row at a time.
 *
 * <p>It used to swallow whatever came back, so a refused lifting looked like a button that does
 * nothing. The rows are lifted one by one, so a failure halfway leaves the earlier ones gone, which is
 * what the reload afterwards is for.
 */
async function handleDeleteGroup(group: GroupedBlock) {
  failure.value = null
  try {
    for (const id of group.blockIds) {
      await lending.deleteBlock(id)
    }
  } catch (e) {
    failure.value = describeFailure(e, t)
  }
  await loadBlocks()
}

</script>

<template>
  <ViewContent
      :title="t('pages.inventory-lending-blocks.title')"
      :subtitle="t('pages.inventory-lending-blocks.subtitle')"
  >
    <div class="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-2 mb-4">
      <ButtonRow pair>
        <SecondaryButton :icon="['fas', 'chevron-left']" @click="router.push({name: routes.lending})">
          {{ t('lending.backToList') }}
        </SecondaryButton>
        <PrimaryButton :icon="['fas', 'plus']" @click="router.push({name: routes.lendingBlocksCreate})">
          {{ t('lending.addBlock') }}
        </PrimaryButton>
      </ButtonRow>
    </div>

    <AsyncSection
        :empty="groupedBlocks.length === 0"
        :empty-message="t('lending.noBlocks')"
        :failure="failure"
        :loading="loading"
    >
      <div class="flex flex-col gap-2">
        <LendingBlockGroupTile v-for="group in groupedBlocks" :key="group.key" :group="group" @delete="handleDeleteGroup(group)"/>
      </div>
    </AsyncSection>
  </ViewContent>
</template>
