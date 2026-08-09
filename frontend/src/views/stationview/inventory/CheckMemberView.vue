/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import { useConfirmAction } from '@/composables/useConfirmAction'
import type { CheckItemResult, CheckResult, MemberCheckState } from '@/api/inventoryCheck'
import { inventoryCheck } from '@/api'
import { useConfigPanel } from '@/composables/useConfigPanel'
import { useAsyncAction } from '@/composables/useAsyncAction'
import { useMemberCheck } from '@/composables/useMemberCheck'
import CheckMemberBody from './checkmemberview/CheckMemberBody.vue'
import { apiErrorStatus } from '@/util/apiError'
import { reportCaughtError } from '@/util/devErrorReporter'

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
 */
function collectResults(current: MemberCheckState): CheckItemResult[] {
  const items: CheckItemResult[] = current.assigned.map(item => ({
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
  if (!state.value || !check.allMarked.value) return
  error.value = ''
  try {
    const completedMemberId = memberId.value
    await inventoryCheck.completeCheck(completedMemberId, { items: collectResults(state.value) })

    const nextId = await inventoryCheck.getNextMember(completedMemberId, teamOnly.value)
    if (!nextId) {
      await router.push({ name: 'inventory-checks' })
      return
    }
    state.value = null
    check.reset()
    await router.replace({
      name: 'inventory-check-member',
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
  router.push({ name: 'inventory-checks' })
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
        :submitting="submitting"
        :item-results="check.itemResults.value"
        :item-notes="check.itemNotes.value"
        :procurement-created="check.procurementCreated.value"
        :slots-not-in-possession="check.slotsNotInPossession.value"
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
        @rapid-mark-not-in-possession="onRapidMarkNotInPossession"
        @rapid-assign="onRapidAssign"
        @rapid-create-and-assign="onRapidCreateAndAssign"
        @rapid-done="checkMode = false"
        @set-result="check.setResult"
        @set-note="check.setNote"
        @unassign="unassign.request"
        @create-procurement="check.createProcurementForItem"
        @change-item="check.changeItem"
        @create-and-change="check.createAndChangeItem"
        @toggle-not-in-possession="check.toggleNotInPossession"
        @assign-to-slot="check.assignToSlot"
        @create-and-assign-to-slot="check.createAndAssignToSlot"
        @update-selection="check.updateSelection"
      />
    </div>
    <ConfirmDeleteModal
      v-model="unassign.show.value"
      :message="t('inventory.check.unassignConfirm')"
      @confirm="unassign.confirm"
    />
  </ViewContent>
</template>
