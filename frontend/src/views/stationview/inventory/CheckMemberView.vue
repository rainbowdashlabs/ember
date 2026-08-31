/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useInventoryRoutes} from '@/composables/useInventoryRoutes'
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import { useConfirmAction } from '@/composables/useConfirmAction'
import type { CheckItemResult, CheckResult, CorrectItemRequest, MemberCheckState } from '@/api/inventoryCheck'
import type { RequiredInventoryItem } from '@/api/inventory'
import type { InventoryItem } from '@/api/inventory'
import { exchanges, inventoryCheck } from '@/api'
import { ExchangeStatus } from '@/api/exchanges'
import { useConfigPanel } from '@/composables/useConfigPanel'
import { useAsyncAction } from '@/composables/useAsyncAction'
import { useMemberCheck, type CheckEntry } from '@/composables/useMemberCheck'
import { apiErrorMessage } from '@/util/apiError'
import RapidExchangeModal from './checkmemberview/RapidExchangeModal.vue'
import CorrectItemModal from './checkmemberview/CorrectItemModal.vue'
import CheckMemberBody from './checkmemberview/CheckMemberBody.vue'
import { apiErrorStatus } from '@/util/apiError'
import { reportCaughtError } from '@/util/devErrorReporter'

const routes = useInventoryRoutes()

const bodyRef = ref<InstanceType<typeof CheckMemberBody> | null>(null)

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const memberId = computed(() => Number(route.params.memberId))
const teamOnly = computed(() => route.query.teamOnly === 'true')
const {config: state, loading, error, reload: loadData} = useConfigPanel<MemberCheckState | null>({
  initial: null,
  fetch: () => inventoryCheck.startCheck(memberId.value),
  formatError: (e) => apiErrorStatus(e) === 409 ? t('inventory.check.locked') : t('common.error'),
})

const check = useMemberCheck(memberId, state, error)

const checkMode = ref(false)

const showExchange = ref(false)
const exchangeEntry = ref<CheckEntry | null>(null)
const exchangeBusy = ref(false)
const exchangeError = ref('')

const showCorrect = ref(false)
const correctItem = ref<InventoryItem | null>(null)
const correctReq = ref<RequiredInventoryItem | null>(null)
const correctBusy = ref(false)
const correctError = ref('')

function startCheckMode() {
  checkMode.value = true
}

function currentRapidEntry() {
  return bodyRef.value?.getCurrentRapidEntry() ?? null
}

function onRapidSetResult(result: CheckResult) {
  const entry = currentRapidEntry()
  if (entry?.type !== 'item') return
  check.setResult(entry.item.id, result)
}

/**
 * The exchange a check raises about the piece in front of whoever is walking it.
 *
 * <p>The form is opened rather than the exchange written straight away: the size one up is a guess,
 * and the reason is the check's words rather than the member's. Both want a look before they stand.
 */
/** Orders the piece an empty slot is waiting for, in the size the walk picked beside it. */
function onRapidCreateProcurement(req: RequiredInventoryItem, slotIndex: number, sizeId: string) {
  check.createProcurementForSlot(req, slotIndex, sizeId ? Number(sizeId) : undefined)
}

/** The same order from the list, where the size sits in that slot's own picker. */
function onSlotProcurement(req: RequiredInventoryItem, slotIndex: number) {
  const sizeId = check.slotSelections.value.get(`create-${req.inventoryId}-${slotIndex}`)
  check.createProcurementForSlot(req, slotIndex, sizeId ? Number(sizeId) : undefined)
}

function onRapidExchange(entry: CheckEntry) {
  if (entry.type !== 'item') return
  exchangeEntry.value = entry
  exchangeError.value = ''
  showExchange.value = true
}

/**
 * Raises the exchange and moves the walk on.
 *
 * <p>How far the exchange has come is decided by the one thing only the person standing there knows:
 * a piece handed over there and then is an exchange whose old piece is already back, and a piece that
 * stays on the member is one that has been announced and no more.
 *
 * <p>Either way the piece is settled for this check, so the walk carries on to the next one instead of
 * offering the same piece again: what was handed in the member no longer has, and what they kept they
 * are wearing.
 */
async function createRapidExchange(payload: {newSizeId: number | null; reason: string; handedIn: boolean}) {
  const entry = exchangeEntry.value
  if (entry?.type !== 'item') return
  exchangeBusy.value = true
  exchangeError.value = ''
  try {
    const created = await exchanges.createExchange({
      memberId: memberId.value,
      itemId: entry.item.id,
      inventoryId: entry.req.inventoryId,
      oldSizeId: entry.item.sizeId ?? undefined,
      newSizeId: payload.newSizeId ?? undefined,
      reason: payload.reason,
    })
    if (payload.handedIn) {
      await exchanges.updateStatus(created.id, {status: ExchangeStatus.RECEIVED, note: payload.reason})
    }
    check.setResult(entry.item.id, payload.handedIn ? 'NOT_IN_POSSESSION' : 'CONFIRMED')
    showExchange.value = false
  } catch (e) {
    exchangeError.value = apiErrorMessage(e) ?? t('common.error')
  } finally {
    exchangeBusy.value = false
  }
}

/**
 * The correction the check raises about the piece in front of whoever is walking it.
 *
 * <p>Opened rather than written straight away: what the member really holds has a size, a number and
 * whatever else the inventory keeps, and none of that can be guessed from the piece being replaced.
 */
function openCorrection(item: InventoryItem, req: RequiredInventoryItem) {
  correctItem.value = item
  correctReq.value = req
  correctError.value = ''
  showCorrect.value = true
}

function onRapidCorrect(entry: CheckEntry) {
  if (entry.type !== 'item') return
  openCorrection(entry.item, entry.req)
}

async function applyCorrection(payload: CorrectItemRequest) {
  correctBusy.value = true
  correctError.value = ''
  try {
    await check.correctItem(payload)
    if (error.value) {
      correctError.value = error.value
      return
    }
    showCorrect.value = false
  } finally {
    correctBusy.value = false
  }
}

/**
 * Steps the walk back onto the piece it just left, taking off what was said about it.
 *
 * <p>Nothing has been sent yet while a check is being walked, so a decision is still the walker's
 * to change. Only the one step back is offered: the list behind the walk is where a check is
 * reworked in full.
 */
function onRapidUndoResult(itemId: number) {
  check.forgetResult(itemId)
}

function onRapidUndoNotInPossession(inventoryId: number, slotIndex: number) {
  check.toggleNotInPossession(inventoryId, slotIndex)
}

function onRapidMarkNotInPossession() {
  const entry = currentRapidEntry()
  if (entry?.type !== 'slot') return
  check.toggleNotInPossession(entry.req.inventoryId, entry.slotIndex)
}

async function onRapidAssign(itemIdStr: string) {
  await check.assignItem(Number(itemIdStr))
}

async function onRapidCreateAndAssign(sizeIdStr: string) {
  const entry = currentRapidEntry()
  if (entry?.type !== 'slot') return
  await check.createAndAssign(entry.req.inventoryId, sizeIdStr ? Number(sizeIdStr) : null)
}

const unassign = useConfirmAction<number>({
  onConfirm: itemId => check.unassignItem(itemId),
  error,
})

/**
 * Collects the marks into the completion payload. Empty slots the member does not hold are sent
 * per slot rather than per item, since there is no item to name.
 *
 * <p>Only what was actually marked is sent. A check walked halfway through is worth recording for the
 * pieces it did look at, and a piece nobody looked at keeps whatever the last check said about it
 * rather than being written down as anything.
 */
function collectResults(current: MemberCheckState): CheckItemResult[] {
  const items: CheckItemResult[] = current.assigned
    .filter(item => check.itemResults.value.has(item.id))
    .map(item => ({
      itemId: item.id,
      result: check.itemResults.value.get(item.id)!,
      note: check.itemNotes.value.get(item.id) ?? '',
    }))
  for (const req of current.required) {
    for (let i = 1; i <= check.emptySlotCount(req); i++) {
      if (check.slotsNotInPossession.value.has(`${req.inventoryId}-${i}`)) {
        items.push({ inventoryId: req.inventoryId, result: 'NOT_IN_POSSESSION', note: '' })
      }
    }
  }
  return items
}

const {running: submitting, run: submit} = useAsyncAction(async () => {
  if (!state.value) return
  const results = collectResults(state.value)
  if (results.length === 0) return
  error.value = ''
  try {
    const completedMemberId = memberId.value
    await inventoryCheck.completeCheck(completedMemberId, { items: results })

    const nextId = await inventoryCheck.getNextMember(completedMemberId, teamOnly.value)
    if (!nextId) {
      await router.push({ name: routes.checks })
      return
    }
    state.value = null
    check.reset()
    await router.replace({
      name: routes.checkMember,
      params: { memberId: nextId },
      query: { teamOnly: teamOnly.value ? 'true' : 'false' },
    })
    await loadData()
  } catch {
    error.value = t('common.error')
  }
})

async function cancel() {
  try {
    await inventoryCheck.cancelCheck(memberId.value)
  } catch (e) {
    reportCaughtError(e, 'member check cancellation')
  }
  router.push({ name: routes.checks })
}
</script>

<template>
  <ViewContent
      :title="t('pages.inventory-check-member.title')"
      :subtitle="t('pages.inventory-check-member.subtitle')"
  >
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>
      <CheckMemberBody
        v-if="!loading && state"
        ref="bodyRef"
        :state="state"
        :check-mode="checkMode"
        :unchecked-entries="check.uncheckedEntries.value"
        :all-marked="check.allMarked.value"
        :any-marked="check.anyMarked.value"
        :submitting="submitting"
        :item-results="check.itemResults.value"
        :item-notes="check.itemNotes.value"
        :slots-not-in-possession="check.slotsNotInPossession.value"
        :slot-procurements="check.slotProcurements.value"
        :slot-selections="check.slotSelections.value"
        :assigned-for-inventory="check.assignedForInventory"
        :available-for-inventory="check.availableForInventory"
        :empty-slot-count="check.emptySlotCount"
        :size-label="check.sizeLabel"
        :item-label="check.itemLabel"
        @start-check-mode="startCheckMode"
        @mark-all-confirmed="check.markAllConfirmed"
        @cancel="cancel"
        @submit="submit"
        @rapid-set-result="onRapidSetResult"
        @rapid-exchange="onRapidExchange"
        @rapid-create-procurement="onRapidCreateProcurement"
        @rapid-correct="onRapidCorrect"
        @rapid-mark-not-in-possession="onRapidMarkNotInPossession"
        @rapid-undo-result="onRapidUndoResult"
        @rapid-undo-not-in-possession="onRapidUndoNotInPossession"
        @rapid-assign="onRapidAssign"
        @rapid-create-and-assign="onRapidCreateAndAssign"
        @rapid-done="checkMode = false"
        @set-result="check.setResult"
        @set-note="check.setNote"
        @unassign="unassign.request"
        @correct="openCorrection"
        @toggle-not-in-possession="check.toggleNotInPossession"
        @assign-to-slot="check.assignToSlot"
        @create-and-assign-to-slot="check.createAndAssignToSlot"
        @create-procurement-for-slot="onSlotProcurement"
        @update-selection="check.updateSelection"
      />
    </div>
    <ConfirmDeleteModal
      v-model="unassign.show.value"
      :message="t('inventory.check.unassignConfirm')"
      @confirm="unassign.confirm"
    />

    <RapidExchangeModal
        v-model="showExchange"
        :busy="exchangeBusy"
        :current-size-id="exchangeEntry?.type === 'item' ? exchangeEntry.item.sizeId : null"
        :error="exchangeError"
        :item-name="exchangeEntry?.type === 'item' ? exchangeEntry.item.name : ''"
        :sizes="exchangeEntry?.type === 'item' ? exchangeEntry.req.sizes : []"
        @confirm="createRapidExchange"
    />

    <CorrectItemModal
        v-model="showCorrect"
        :available-items="correctReq ? check.availableForInventory(correctReq.inventoryId) : []"
        :busy="correctBusy"
        :error="correctError"
        :item="correctItem"
        :item-label="check.itemLabel"
        :req="correctReq"
        @confirm="applyCorrection"
    />
  </ViewContent>
</template>
