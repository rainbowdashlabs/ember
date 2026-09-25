/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useInventoryRoutes} from '@/composables/useInventoryRoutes'
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import BlockFormBody from './lendingblockscreateview/BlockFormBody.vue'
import type {BlockEntry} from './lendingblockscreateview/types'
import * as lending from '@/api/lending'
import {inventory} from '@/api'
import {isAvailable, type Inventory} from '@/api/inventory'
import {useSession} from '@/composables/useSession'
import {useAsyncAction} from '@/composables/useAsyncAction'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import {describeFailure} from '@/util/failure'
import {useAsyncLoader} from '@/composables/useAsyncLoader'

const routes = useInventoryRoutes()

const {t} = useI18n()
const router = useRouter()
const {loaded} = useSession()

const inventories = ref<Inventory[]>([])

const blockFrom = ref('')
const blockTo = ref('')
const reason = ref('')
const entries = ref<BlockEntry[]>([])
const addInventoryId = ref('')

const availableInventories = computed(() =>
    inventories.value.filter(inv => !entries.value.some(e => e.inventoryId === inv.id))
)

const isFullBlock = computed(() => entries.value.length === 0)

/** What could not be read while the form was being filled in, which used to vanish without a word. */
const {
  loading: loadingInventories,
  failure: loadFailure,
  reload: loadInventories,
} = useAsyncLoader(async () => {
  inventories.value = await inventory.listInventories()
}, {autoLoad: false})

async function addEntry() {
  const invId = Number(addInventoryId.value)
  if (!invId) return
  const inv = inventories.value.find(i => i.id === invId)
  if (!inv) return

  const entry: BlockEntry = {
    inventoryId: invId,
    inventoryName: inv.name ?? `#${invId}`,
    allItems: true,
    items: [],
    selectedItemIds: new Set(),
    loadingItems: true,
  }
  entries.value = [...entries.value, entry]
  addInventoryId.value = ''

  try {
    const items = await inventory.listItems(invId)
    // Custody, not the assignment: gear in transit or already with a partner is not free either
    entry.items = items.filter(item => isAvailable(item.custody))
  } catch (e) {
    loadFailure.value = {...describeFailure(e, t), message: t('lending.availableUnreadable')}
  } finally {
    entry.loadingItems = false
  }
}

function removeEntry(invId: number) {
  entries.value = entries.value.filter(e => e.inventoryId !== invId)
}

function toggleAllItems(entry: BlockEntry, val: boolean) {
  entry.allItems = val
  if (val) entry.selectedItemIds = new Set()
}

function toggleItem(entry: BlockEntry, itemId: number) {
  const set = new Set(entry.selectedItemIds)
  if (set.has(itemId)) set.delete(itemId)
  else set.add(itemId)
  entry.selectedItemIds = set
}

onMounted(() => {
  if (loaded.value) loadInventories()
})

watch(loaded, (v) => {
  if (v) loadInventories()
})

/**
 * Writes the block, which is one row for a whole stop and one per inventory or piece otherwise.
 *
 * <p>Several rows behind one button, so a failure halfway leaves the ones before it standing. The
 * failure's guidance is to look again, which here means the list of blocks rather than this form.
 */
const {running: saving, failure: saveFailure, run: handleCreate} = useAsyncAction(async () => {
  if (!blockFrom.value || !blockTo.value) return
  if (isFullBlock.value) {
    await lending.createBlock({
      blockFrom: blockFrom.value,
      blockTo: blockTo.value,
      reason: reason.value,
    })
  } else {
    for (const entry of entries.value) {
      if (entry.allItems) {
        await lending.createBlock({
          blockFrom: blockFrom.value,
          blockTo: blockTo.value,
          reason: reason.value,
          inventoryId: entry.inventoryId,
        })
      } else {
        for (const itemId of entry.selectedItemIds) {
          await lending.createBlock({
            blockFrom: blockFrom.value,
            blockTo: blockTo.value,
            reason: reason.value,
            inventoryId: entry.inventoryId,
            itemId,
          })
        }
      }
    }
  }
  router.push({name: routes.lendingBlocks})
})

function goBack() {
  router.push({name: routes.lendingBlocks})
}
</script>

<template>
  <ViewContent
      :title="t('pages.inventory-lending-blocks-create.title')"
      :subtitle="t('pages.inventory-lending-blocks-create.subtitle')"
  >
    <div class="flex items-center gap-3 mb-6">
      <SecondaryButton :icon="['fas', 'chevron-left']" @click="goBack">
        {{ t('common.back') }}
      </SecondaryButton>
      <SectionHeader>{{ t('lending.addBlock') }}</SectionHeader>
    </div>

    <FailureAlert :failure="loadFailure ?? saveFailure" class="mb-4"/>

    <Spinner v-if="loadingInventories"/>

    <BlockFormBody
        v-else
        v-model:block-from="blockFrom"
        v-model:block-to="blockTo"
        v-model:reason="reason"
        v-model:add-inventory-id="addInventoryId"
        :entries="entries"
        :available-inventories="availableInventories"
        :saving="saving"
        @add="addEntry"
        @remove="removeEntry"
        @toggle-all="toggleAllItems"
        @toggle-item="toggleItem"
        @cancel="goBack"
        @create="handleCreate"
    />
  </ViewContent>
</template>
