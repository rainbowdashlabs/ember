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
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import AsyncSection from '@/components/feedback/AsyncSection.vue'
import Alert from '@/components/feedback/Alert.vue'
import type {Inventory, InventoryDetail, InventoryItem} from '@/api/inventory'
import {inventory} from '@/api'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useInventoryRoutes} from '@/composables/useInventoryRoutes'
import MoveItemsTable from './moveitemsview/MoveItemsTable.vue'

/**
 * Moving pieces out of one inventory and into another.
 *
 * <p>Splitting an inventory in two used to mean deleting the pieces and writing them down again,
 * which threw away their identifiers, who had them, and everywhere they had been. Here the pieces
 * stay the pieces they were and only the drawer they are filed under changes, which is what makes
 * the choice between one thing in many copies and a drawer of different things something a station
 * can put right rather than something it is stuck with.
 */
const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const routes = useInventoryRoutes()

const inventoryId = computed(() => Number(route.params.id))

const detail = ref<InventoryDetail | null>(null)
const items = ref<InventoryItem[]>([])
const inventories = ref<Inventory[]>([])
const selected = ref<Set<number>>(new Set())
const targetId = ref('')
const moved = ref(0)

const {loading, error} = useAsyncLoader(async () => {
  const [inv, invItems, all] = await Promise.all([
    inventory.getInventory(inventoryId.value),
    inventory.listItems(inventoryId.value),
    inventory.listInventories(),
  ])
  detail.value = inv
  items.value = invItems
  inventories.value = all
})

/** Everything of this station except the one being emptied: a piece cannot move to where it is. */
const targets = computed(() => inventories.value.filter(i => i.id !== inventoryId.value))

const sizeLabel = computed(() => {
  const byId = new Map((detail.value?.sizes ?? []).map(size => [size.id, size.label]))
  return (item: InventoryItem) => (item.sizeId ? (byId.get(item.sizeId) ?? '') : '')
})

/** Whether the chosen target keeps a size of the same name, which decides what the piece arrives with. */
const targetSizeLabels = ref<Set<string>>(new Set())

async function onTargetSelected() {
  targetSizeLabels.value = new Set()
  const id = Number(targetId.value)
  if (!id) return
  const target = await inventory.getInventory(id)
  targetSizeLabels.value = new Set(
      (target.sizes ?? []).map(size => size.label ?? '').filter(label => label !== ''),
  )
}

function toggle(itemId: number) {
  const next = new Set(selected.value)
  if (next.has(itemId)) next.delete(itemId)
  else next.add(itemId)
  selected.value = next
}

function toggleAll() {
  selected.value = selected.value.size === items.value.length
      ? new Set()
      : new Set(items.value.map(item => item.id))
}

const {running: moving, error: moveError, run: runMove} = useAsyncAction(async () => {
  const id = Number(targetId.value)
  const chosen = [...selected.value]
  for (const itemId of chosen) {
    await inventory.moveItem(itemId, id)
  }
  moved.value = chosen.length
  selected.value = new Set()
  items.value = await inventory.listItems(inventoryId.value)
  return true
})
</script>

<template>
  <ViewContent
      :title="t('pages.inventory-move.title')"
      :subtitle="t('pages.inventory-move.subtitle')"
  >
    <div class="space-y-6">
      <SecondaryButton :icon="['fas', 'chevron-left']"
                       @click="router.push({name: routes.edit, params: {id: inventoryId}})">
        {{ t('inventory.move.back') }}
      </SecondaryButton>

      <AsyncSection :loading="loading" :error="error" :empty="items.length === 0"
                    :empty-message="t('inventory.move.nothingToMove')">
        <div class="space-y-6">
          <NeutralContainer class="space-y-4">
            <SubHeader>{{ t('inventory.move.target') }}</SubHeader>
            <p class="text-sm text-(--text-muted)">{{ t('inventory.move.explainer') }}</p>
            <div class="space-y-1">
              <FieldLabel>{{ t('inventory.move.targetLabel') }}</FieldLabel>
              <SelectInput v-model="targetId" data-testid="move-target" @change="onTargetSelected">
                <option disabled value="">{{ t('inventory.move.selectTarget') }}</option>
                <option v-for="target in targets" :key="target.id" :value="String(target.id)">
                  {{ target.name }}
                </option>
              </SelectInput>
            </div>
          </NeutralContainer>

          <MoveItemsTable
              :items="items"
              :selected="selected"
              :size-label="sizeLabel"
              :target-size-labels="targetSizeLabels"
              :target-chosen="!!targetId"
              @toggle="toggle"
              @toggle-all="toggleAll"
          />

          <Alert v-if="moveError" variant="error">{{ moveError }}</Alert>
          <Alert v-if="moved > 0" variant="success" data-testid="move-done">
            {{ t('inventory.move.moved', {count: moved}) }}
          </Alert>

          <PrimaryButton :disabled="!targetId || selected.size === 0 || moving"
                         :icon="['fas', 'right-left']" data-testid="move-submit"
                         @click="runMove()">
            {{ t('inventory.move.submit', {count: selected.size}) }}
          </PrimaryButton>
        </div>
      </AsyncSection>
    </div>
  </ViewContent>
</template>
