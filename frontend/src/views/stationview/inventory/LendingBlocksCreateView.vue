/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import * as lending from '@/api/lending'
import {inventory} from '@/api'
import type {Inventory, InventoryItem} from '@/api/types'
import {useSession} from '@/composables/useSession'

const {t} = useI18n()
const router = useRouter()
const {loaded} = useSession()

interface BlockEntry {
  inventoryId: number
  inventoryName: string
  allItems: boolean
  items: InventoryItem[]
  selectedItemIds: Set<number>
  loadingItems: boolean
}

const inventories = ref<Inventory[]>([])
const loadingInventories = ref(true)

const blockFrom = ref('')
const blockTo = ref('')
const reason = ref('')
const entries = ref<BlockEntry[]>([])
const addInventoryId = ref('')
const saving = ref(false)

const availableInventories = computed(() =>
    inventories.value.filter(inv => !entries.value.some(e => e.inventoryId === inv.id))
)

const isFullBlock = computed(() => entries.value.length === 0)

async function loadInventories() {
  loadingInventories.value = true
  try {
    inventories.value = await inventory.listInventories()
  } catch { /* ignore */ } finally {
    loadingInventories.value = false
  }
}

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
    // Filter to unassigned items (available for blocking)
    const available = items.filter(item => !item.assignedTo && !item.lostAt)
    entry.items = available
  } catch { /* ignore */ } finally {
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

async function handleCreate() {
  if (!blockFrom.value || !blockTo.value) return
  saving.value = true
  try {
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
    router.push({name: 'inventory-lending-blocks'})
  } catch { /* ignore */ } finally {
    saving.value = false
  }
}
</script>

<template>
  <ViewContent>
    <div class="flex items-center gap-3 mb-6">
      <SecondaryButton :icon="['fas', 'chevron-left']" @click="router.push({name: 'inventory-lending-blocks'})">
        {{ t('common.back') }}
      </SecondaryButton>
      <SectionHeader>{{ t('lending.addBlock') }}</SectionHeader>
    </div>

    <Spinner v-if="loadingInventories"/>

    <div v-else class="space-y-5 max-w-2xl">
      <!-- Date range -->
      <NeutralContainer class="space-y-4">
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div>
            <FieldLabel class="mb-1">{{ t('lending.blockFrom') }}</FieldLabel>
            <DateInput v-model="blockFrom"/>
          </div>
          <div>
            <FieldLabel class="mb-1">{{ t('lending.blockTo') }}</FieldLabel>
            <DateInput v-model="blockTo"/>
          </div>
        </div>
        <div>
          <FieldLabel class="mb-1">{{ t('lending.blockReason') }}</FieldLabel>
          <TextInput v-model="reason" :placeholder="t('lending.blockReasonPlaceholder')"/>
        </div>
      </NeutralContainer>

      <!-- Scope -->
      <div>
        <FieldLabel class="mb-2">{{ t('lending.blockScope') }}</FieldLabel>
        <MutedText v-if="entries.length === 0" tag="div" size="sm" class="mb-3">{{ t('lending.blockScopeHint') }}</MutedText>

        <!-- Inventory tiles -->
        <div v-if="entries.length > 0" class="space-y-3 mb-3">
          <NeutralContainer v-for="entry in entries" :key="entry.inventoryId">
            <div class="flex items-center justify-between mb-2">
              <SubHeader>{{ entry.inventoryName }}</SubHeader>
              <DeleteButton @click="removeEntry(entry.inventoryId)"/>
            </div>

            <FieldLabel inline class="cursor-pointer">
              <ToggleInput :model-value="entry.allItems" @update:model-value="toggleAllItems(entry, $event)"/>
              {{ t('lending.blockItemAll') }}
            </FieldLabel>

            <!-- Item list (when allItems is false) -->
            <div v-if="!entry.allItems" class="mt-3">
              <Spinner v-if="entry.loadingItems" size="sm"/>
              <div v-else-if="entry.items.length === 0">
                <MutedText size="sm">{{ t('lending.noAvailableItems') }}</MutedText>
              </div>
              <div v-else class="flex flex-wrap gap-2">
                <FieldLabel v-for="item in entry.items" :key="item.id" inline class="cursor-pointer">
                  <CheckboxInput :model-value="entry.selectedItemIds.has(item.id)" @update:model-value="toggleItem(entry, item.id)"/>
                  {{ item.name }}{{ item.internalId ? ` (${item.internalId})` : '' }}
                </FieldLabel>
              </div>
            </div>
          </NeutralContainer>
        </div>

        <!-- Add inventory -->
        <div class="flex gap-2 items-end">
          <div class="flex-1">
            <SelectInput v-model="addInventoryId">
              <option value="">{{ t('lending.selectInventory') }}</option>
              <option v-for="inv in availableInventories" :key="inv.id" :value="String(inv.id)">
                {{ inv.name }}
              </option>
            </SelectInput>
          </div>
          <SecondaryButton :icon="['fas', 'plus']" :disabled="!addInventoryId" @click="addEntry">
            {{ t('lending.addInventory') }}
          </SecondaryButton>
        </div>
      </div>

      <!-- Actions -->
      <div class="flex justify-end gap-2">
        <SecondaryButton @click="router.push({name: 'inventory-lending-blocks'})">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="saving || !blockFrom || !blockTo" @click="handleCreate">
          {{ t('common.save') }}
        </PrimaryButton>
      </div>
    </div>
  </ViewContent>
</template>
