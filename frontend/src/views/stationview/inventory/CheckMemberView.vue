/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import InfoButton from '@/components/button/InfoButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import type { CheckResult, InventoryItem, MemberCheckState, RequiredInventoryItem } from '@/api/types'
import { inventoryCheck } from '@/api'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const memberId = computed(() => Number(route.params.memberId))
const state = ref<MemberCheckState | null>(null)
const loading = ref(true)
const error = ref('')
const submitting = ref(false)

// Per-item check results and notes
const itemResults = ref<Map<number, CheckResult>>(new Map())
const itemNotes = ref<Map<number, string>>(new Map())

// Per-slot assign selection (key: "inventoryId-slotIndex")
const slotSelections = ref<Map<string, string>>(new Map())

// Empty slots marked as "not in possession" (key: "inventoryId-slotIndex")
const slotsNotInPossession = ref<Set<string>>(new Set())

function setResult(itemId: number, result: CheckResult) {
  const current = itemResults.value.get(itemId)
  if (current === result) {
    itemResults.value.delete(itemId)
  } else {
    itemResults.value.set(itemId, result)
  }
  itemResults.value = new Map(itemResults.value)
}

function getResult(itemId: number): CheckResult | undefined {
  return itemResults.value.get(itemId)
}

function setNote(itemId: number, note: string) {
  itemNotes.value.set(itemId, note)
  itemNotes.value = new Map(itemNotes.value)
}

function getNote(itemId: number): string {
  return itemNotes.value.get(itemId) ?? ''
}

// Items assigned to this member for a specific inventory
function assignedForInventory(inventoryId: number): InventoryItem[] {
  if (!state.value) return []
  return state.value.assigned.filter(i => i.inventoryId === inventoryId)
}

// Unassigned items available for a specific inventory
function availableForInventory(inventoryId: number): InventoryItem[] {
  if (!state.value) return []
  return state.value.unassigned[inventoryId] ?? []
}

// Number of empty slots for an inventory
function emptySlotCount(req: RequiredInventoryItem): number {
  return Math.max(0, req.requiredQuantity - req.assignedQuantity)
}

function toggleNotInPossession(inventoryId: number, slotIdx: number) {
  const key = `${inventoryId}-${slotIdx}`
  const newSet = new Set(slotsNotInPossession.value)
  if (newSet.has(key)) newSet.delete(key)
  else newSet.add(key)
  slotsNotInPossession.value = newSet
}

const allMarked = computed(() => {
  if (!state.value) return false
  const assignedMarked = state.value.assigned.every(item => itemResults.value.has(item.id))
  const emptyMarked = state.value.required.every(req => {
    const empty = emptySlotCount(req)
    for (let i = 1; i <= empty; i++) {
      if (!slotsNotInPossession.value.has(`${req.inventoryId}-${i}`)) return false
    }
    return true
  })
  return assignedMarked && emptyMarked
})

function markAllConfirmed() {
  if (!state.value) return
  for (const item of state.value.assigned) {
    if (!itemResults.value.has(item.id)) {
      itemResults.value.set(item.id, 'CONFIRMED')
    }
  }
  itemResults.value = new Map(itemResults.value)
}

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    state.value = await inventoryCheck.startCheck(memberId.value)
  } catch (e: any) {
    if (e?.response?.status === 409) {
      error.value = t('inventory.check.locked')
    } else {
      error.value = t('common.error')
    }
  } finally {
    loading.value = false
  }
}

async function assignToSlot(inventoryId: number, slotIndex: number, oldItemId?: number) {
  const key = `${inventoryId}-${slotIndex}`
  const selectedId = slotSelections.value.get(key)
  if (!selectedId) return
  error.value = ''
  try {
    state.value = await inventoryCheck.assignItem(memberId.value, Number(selectedId), oldItemId)
    slotSelections.value.delete(key)
    slotSelections.value = new Map(slotSelections.value)
  } catch {
    error.value = t('common.error')
  }
}

async function changeItem(currentItemId: number, _inventoryId: number) {
  const key = `change-${currentItemId}`
  const selectedId = slotSelections.value.get(key)
  if (!selectedId) return
  error.value = ''
  try {
    // Remove check result for old item
    itemResults.value.delete(currentItemId)
    itemResults.value = new Map(itemResults.value)
    state.value = await inventoryCheck.assignItem(memberId.value, Number(selectedId), currentItemId)
    slotSelections.value.delete(key)
    slotSelections.value = new Map(slotSelections.value)
  } catch {
    error.value = t('common.error')
  }
}

async function unassignItem(itemId: number) {
  if (!confirm(t('inventory.check.unassignConfirm'))) return
  error.value = ''
  try {
    itemResults.value.delete(itemId)
    itemNotes.value.delete(itemId)
    itemResults.value = new Map(itemResults.value)
    itemNotes.value = new Map(itemNotes.value)
    state.value = await inventoryCheck.unassignItem(memberId.value, itemId)
  } catch {
    error.value = t('common.error')
  }
}

async function createAndChangeItem(currentItemId: number, req: RequiredInventoryItem) {
  const key = `create-change-${currentItemId}`
  const selectedSizeId = slotSelections.value.get(key)
  error.value = ''
  try {
    itemResults.value.delete(currentItemId)
    itemResults.value = new Map(itemResults.value)
    state.value = await inventoryCheck.createAndAssign(
      memberId.value,
      req.inventoryId,
      selectedSizeId ? Number(selectedSizeId) : null,
      currentItemId,
    )
    slotSelections.value.delete(key)
    slotSelections.value = new Map(slotSelections.value)
  } catch {
    error.value = t('common.error')
  }
}

async function createAndAssignToSlot(req: RequiredInventoryItem, slotIndex: number) {
  const key = `create-${req.inventoryId}-${slotIndex}`
  const selectedSizeId = slotSelections.value.get(key)
  error.value = ''
  try {
    state.value = await inventoryCheck.createAndAssign(
      memberId.value,
      req.inventoryId,
      selectedSizeId ? Number(selectedSizeId) : null,
    )
    slotSelections.value.delete(key)
    slotSelections.value = new Map(slotSelections.value)
  } catch {
    error.value = t('common.error')
  }
}

async function submit() {
  if (!state.value || !allMarked.value) return
  submitting.value = true
  error.value = ''
  try {
    const items: import('@/api/types').CheckItemResult[] = state.value.assigned.map(item => ({
      itemId: item.id,
      result: itemResults.value.get(item.id)!,
      note: itemNotes.value.get(item.id) ?? '',
    }))
    // Add empty slot "not in possession" entries
    for (const req of state.value.required) {
      const empty = emptySlotCount(req)
      for (let i = 1; i <= empty; i++) {
        if (slotsNotInPossession.value.has(`${req.inventoryId}-${i}`)) {
          items.push({ inventoryId: req.inventoryId, result: 'NOT_IN_POSSESSION', note: '' })
        }
      }
    }
    const completedMemberId = memberId.value
    await inventoryCheck.completeCheck(completedMemberId, { items })

    const nextId = await inventoryCheck.getNextMember(completedMemberId)
    if (nextId) {
      // Reset state before navigating
      state.value = null
      itemResults.value = new Map()
      itemNotes.value = new Map()
      slotSelections.value = new Map()
      slotsNotInPossession.value = new Set()
      await router.replace({ name: 'inventory-check-member', params: { memberId: nextId } })
      await loadData()
    } else {
      await router.push({ name: 'inventory-checks' })
    }
  } catch {
    error.value = t('common.error')
  } finally {
    submitting.value = false
  }
}

async function cancel() {
  try {
    await inventoryCheck.cancelCheck(memberId.value)
  } catch {
    // ignore
  }
  router.push({ name: 'inventory-checks' })
}

function resultClass(itemId: number): string {
  const result = itemResults.value.get(itemId)
  if (!result) return ''
  switch (result) {
    case 'CONFIRMED': return 'ring-2 ring-success bg-success/10'
    case 'NOT_IN_POSSESSION': return 'ring-2 ring-info bg-info/10'
    case 'LOST': return 'ring-2 ring-error bg-error/10'
    default: return ''
  }
}

function sizeLabel(req: RequiredInventoryItem, sizeId?: number | null): string {
  if (!sizeId || !req.sizes.length) return ''
  return req.sizes.find(s => s.id === sizeId)?.label ?? ''
}

function itemLabel(item: InventoryItem, req: RequiredInventoryItem): string {
  const parts = [item.name]
  if (item.internalId) parts.push(`(${item.internalId})`)
  const size = sizeLabel(req, item.sizeId)
  if (size) parts.push(`[${size}]`)
  return parts.join(' ')
}

onMounted(loadData)
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading && state">
        <!-- Header -->
        <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
          <div>
            <SectionHeader>{{ state.memberName }}</SectionHeader>
            <p class="text-sm text-(--text-muted)">{{ t('inventory.check.title') }}</p>
          </div>
          <div class="flex gap-2">
            <SecondaryButton v-if="state.assigned.length > 0" class="text-sm" @click="markAllConfirmed">
              {{ t('inventory.check.markAll') }}
            </SecondaryButton>
            <SecondaryButton @click="cancel">{{ t('inventory.check.cancel') }}</SecondaryButton>
          </div>
        </div>

        <!-- Last check info -->
        <NeutralContainer v-if="state.lastCheck">
          <div class="text-sm text-(--text-muted)">
            {{ t('inventory.check.lastChecked') }}: {{ new Date(state.lastCheck.checkedAt).toLocaleString('de-DE') }}
          </div>
        </NeutralContainer>

        <!-- Inventory sections -->
        <div class="space-y-6">
          <NeutralContainer v-for="req in state.required" :key="req.inventoryId" class="space-y-3">
            <div class="flex items-center justify-between gap-2">
              <SubHeader>{{ req.inventoryName }}</SubHeader>
              <span class="text-sm text-(--text-muted) shrink-0">
                {{ req.assignedQuantity }} / {{ req.requiredQuantity }}
                <span v-if="req.assignedQuantity < req.requiredQuantity" class="text-error">
                  ({{ req.requiredQuantity - req.assignedQuantity }} fehlt)
                </span>
              </span>
            </div>

            <!-- Assigned items -->
            <div class="space-y-2">
              <div
                v-for="item in assignedForInventory(req.inventoryId)"
                :key="item.id"
                class="rounded border border-bg-light-accent/50 dark:border-bg-dark-accent/50 p-3 space-y-2 transition-all"
                :class="resultClass(item.id)"
              >
                <!-- Item info + action buttons -->
                <div class="flex flex-col sm:flex-row sm:items-center gap-2">
                  <div class="flex-1 min-w-0">
                    <div class="font-medium text-sm truncate">
                      {{ item.name }}
                      <span v-if="sizeLabel(req, item.sizeId)" class="font-normal text-(--text-muted)">[{{ sizeLabel(req, item.sizeId) }}]</span>
                    </div>
                    <div v-if="item.internalId" class="text-xs text-(--text-muted)">{{ item.internalId }}</div>
                  </div>
                  <div class="flex gap-1 shrink-0">
                    <SuccessButton
                      class="text-xs px-3 py-1.5 sm:px-2 sm:py-1 flex-1 sm:flex-none"
                      :class="{ 'opacity-40': getResult(item.id) && getResult(item.id) !== 'CONFIRMED' }"
                      @click="setResult(item.id, 'CONFIRMED')"
                    >
                      <font-awesome-icon :icon="['fas', 'check']" />
                    </SuccessButton>
                    <ErrorButton
                      class="text-xs px-3 py-1.5 sm:px-2 sm:py-1 flex-1 sm:flex-none"
                      :class="{ 'opacity-40': getResult(item.id) && getResult(item.id) !== 'LOST' }"
                      @click="setResult(item.id, 'LOST')"
                    >
                      <font-awesome-icon :icon="['fas', 'xmark']" />
                    </ErrorButton>
                    <SecondaryButton
                      class="text-xs px-3 py-1.5 sm:px-2 sm:py-1 flex-1 sm:flex-none"
                      @click="unassignItem(item.id)"
                    >
                      <font-awesome-icon :icon="['fas', 'right-from-bracket']" />
                    </SecondaryButton>
                  </div>
                </div>

                <!-- Note -->
                <TextInput
                  :model-value="getNote(item.id)"
                  :placeholder="t('inventory.check.notePlaceholder')"
                  @update:model-value="setNote(item.id, ($event as string) ?? '')"
                />

                <!-- Change: pick from existing unassigned -->
                <div v-if="availableForInventory(req.inventoryId).length > 0" class="flex flex-col sm:flex-row gap-2">
                  <SelectInput
                    :model-value="slotSelections.get(`change-${item.id}`) ?? ''"
                    class="flex-1"
                    @update:model-value="(v: string | undefined) => { slotSelections.set(`change-${item.id}`, v ?? ''); slotSelections = new Map(slotSelections) }"
                  >
                    <option value="" disabled>{{ t('inventory.check.change') }}...</option>
                    <option v-for="avail in availableForInventory(req.inventoryId)" :key="avail.id" :value="String(avail.id)">
                      {{ itemLabel(avail, req) }}
                    </option>
                  </SelectInput>
                  <PrimaryButton
                    class="text-sm"
                    :disabled="!slotSelections.get(`change-${item.id}`)"
                    @click="changeItem(item.id, req.inventoryId)"
                  >
                    {{ t('inventory.check.change') }}
                  </PrimaryButton>
                </div>

                <!-- Change: create new item by size -->
                <div class="flex flex-col sm:flex-row gap-2">
                  <SelectInput
                    v-if="req.hasSizes && req.sizes.length > 0"
                    :model-value="slotSelections.get(`create-change-${item.id}`) ?? ''"
                    class="flex-1"
                    @update:model-value="(v: string | undefined) => { slotSelections.set(`create-change-${item.id}`, v ?? ''); slotSelections = new Map(slotSelections) }"
                  >
                    <option value="" disabled>{{ t('inventory.check.selectSize') }}</option>
                    <option v-for="size in req.sizes" :key="size.id" :value="String(size.id)">{{ size.label }}</option>
                  </SelectInput>
                  <SecondaryButton
                    class="text-sm"
                    :disabled="req.hasSizes && req.sizes.length > 0 && !slotSelections.get(`create-change-${item.id}`)"
                    @click="createAndChangeItem(item.id, req)"
                  >
                    <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />
                    {{ t('inventory.check.create') }}
                  </SecondaryButton>
                </div>
              </div>
            </div>

            <!-- Empty slots -->
            <div v-for="slotIdx in emptySlotCount(req)" :key="`empty-${req.inventoryId}-${slotIdx}`"
              class="rounded border-2 border-dashed p-3 space-y-2 transition-all"
              :class="slotsNotInPossession.has(`${req.inventoryId}-${slotIdx}`) ? 'border-info ring-2 ring-info bg-info/10' : 'border-bg-light-accent dark:border-bg-dark-accent'"
            >
              <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
                <span class="text-sm text-(--text-muted)">{{ t('inventory.check.emptySlot') }}</span>
                <InfoButton
                  class="text-xs px-2 py-1.5 sm:py-1 w-full sm:w-auto"
                  :class="{ 'opacity-100': slotsNotInPossession.has(`${req.inventoryId}-${slotIdx}`), 'opacity-60': !slotsNotInPossession.has(`${req.inventoryId}-${slotIdx}`) }"
                  @click="toggleNotInPossession(req.inventoryId, slotIdx)"
                >
                  <font-awesome-icon :icon="['fas', 'ban']" class="mr-1" />
                  {{ t('inventory.check.notInPossession') }}
                </InfoButton>
              </div>

              <!-- Assign existing unassigned item -->
              <div v-if="availableForInventory(req.inventoryId).length > 0" class="flex flex-col sm:flex-row gap-2">
                <SelectInput
                  :model-value="slotSelections.get(`${req.inventoryId}-${slotIdx}`) ?? ''"
                  class="flex-1"
                  @update:model-value="(v: string | undefined) => { slotSelections.set(`${req.inventoryId}-${slotIdx}`, v ?? ''); slotSelections = new Map(slotSelections) }"
                >
                  <option value="" disabled>{{ t('inventory.check.selectItem') }}</option>
                  <option v-for="avail in availableForInventory(req.inventoryId)" :key="avail.id" :value="String(avail.id)">
                    {{ itemLabel(avail, req) }}
                  </option>
                </SelectInput>
                <PrimaryButton
                  class="text-sm"
                  :disabled="!slotSelections.get(`${req.inventoryId}-${slotIdx}`)"
                  @click="assignToSlot(req.inventoryId, slotIdx)"
                >
                  {{ t('inventory.check.assign') }}
                </PrimaryButton>
              </div>

              <!-- Create new item on the fly -->
              <div class="flex flex-col sm:flex-row gap-2">
                <SelectInput
                  v-if="req.hasSizes && req.sizes.length > 0"
                  :model-value="slotSelections.get(`create-${req.inventoryId}-${slotIdx}`) ?? ''"
                  class="flex-1"
                  @update:model-value="(v: string | undefined) => { slotSelections.set(`create-${req.inventoryId}-${slotIdx}`, v ?? ''); slotSelections = new Map(slotSelections) }"
                >
                  <option value="" disabled>{{ t('inventory.check.selectSize') }}</option>
                  <option v-for="size in req.sizes" :key="size.id" :value="String(size.id)">{{ size.label }}</option>
                </SelectInput>
                <SecondaryButton
                  class="text-sm"
                  :disabled="req.hasSizes && req.sizes.length > 0 && !slotSelections.get(`create-${req.inventoryId}-${slotIdx}`)"
                  @click="createAndAssignToSlot(req, slotIdx)"
                >
                  <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />
                  {{ t('inventory.check.create') }}
                </SecondaryButton>
              </div>
            </div>
          </NeutralContainer>
        </div>

        <!-- Submit (sticky on mobile) -->
        <div class="sticky bottom-0 bg-bg-light dark:bg-bg-dark py-4 -mx-4 px-4 sm:mx-0 sm:px-0 sm:relative border-t border-bg-light-accent/50 dark:border-bg-dark-accent/50 sm:border-0 flex justify-end gap-3">
          <SecondaryButton @click="cancel">{{ t('inventory.check.cancel') }}</SecondaryButton>
          <PrimaryButton :disabled="!allMarked || submitting" @click="submit">
            {{ submitting ? t('inventory.check.submitting') : t('inventory.check.complete') }}
          </PrimaryButton>
        </div>
      </template>
    </div>
  </ViewContent>
</template>
