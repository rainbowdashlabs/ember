/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useInventoryRoutes} from '@/composables/useInventoryRoutes'
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Alert from '@/components/feedback/Alert.vue'
import AsyncSection from '@/components/feedback/AsyncSection.vue'
import {inventory, movements, stationMembers} from '@/api'
import type {HandOutMode} from '@/components/inventory/HandOutChoice.vue'
import type {InventoryItem, InventorySize, MyInventoryItem} from '@/api/inventory'
import {StationPermission, type StationMember} from '@/api/types'
import {useSession} from '@/composables/useSession'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useFlashMessage} from '@/composables/useFlashMessage'
import {normaliseScannedPayload} from '@/components/scanner/useBarcodeScanner'
import UnknownScanModal from '@/views/stationview/inventory/UnknownScanModal.vue'
import MemberInventoryHeader from './memberinventoryview/MemberInventoryHeader.vue'
import ReturnEverythingBar from './memberinventoryview/ReturnEverythingBar.vue'
import MemberInventoryScanPanel from './memberinventoryview/MemberInventoryScanPanel.vue'
import MemberInventoryGroups from './memberinventoryview/MemberInventoryGroups.vue'
import MovementsPanel from '@/components/inventory/MovementsPanel.vue'
import MovementWizard from './movementwizard/MovementWizard.vue'
import type {WizardPrefill} from './movementwizard/useMovementWizard'
import {MovementPurpose} from '@/api/movements'
import {apiErrorMessage} from '@/util/apiError'

const routes = useInventoryRoutes()

const {t} = useI18n()
const route = useRoute()
const router = useRouter()

const {canManageInventory, hasPermission} = useSession()
const canAssign = computed(() =>
    hasPermission(StationPermission.INVENTORY_ASSIGN) || hasPermission(StationPermission.INVENTORY_EDIT))

const scanValue = ref('')
const handOutMode = ref<HandOutMode>('NOW')
const unknownScanCode = ref<string | null>(null)

const {message: scanError, flash: flashScanError} = useFlashMessage(3500)
const {message: scanSuccess, flash: flashScanSuccess} = useFlashMessage(2500)

/** Hands the scanned piece over, or writes down that it is to be handed over. */
async function assignToCurrentMember(item: InventoryItem | {id: number; name?: string; inventoryId?: number}) {
  if (handOutMode.value === 'PLANNED') {
    await movements.planHandOut(memberId.value, item.id, item.inventoryId)
    flashScanSuccess(t('inventory.handOut.plannedFlash', {name: item.name ?? ''}))
  } else {
    await inventory.assignItem(item.id, {
      memberId: memberId.value,
      memberName: member.value?.name ?? '',
    })
    flashScanSuccess(t('inventory.assign.assigned', {name: item.name ?? ''}))
  }
  items.value = await inventory.memberItems(memberId.value)
}

async function onCameraScan(value: string) {
  if (scanBusy.value) return
  scanValue.value = normaliseScannedPayload(value)
  await handleScanAssign()
}

const {running: scanBusy, error: scanAssignError, run: runScanAssign} = useAsyncAction(async (term: string) => {
  const item = await inventory.findByInternalId(term)
  if (!item) {
    unknownScanCode.value = term
    return
  }
  if (item.assignedTo === memberId.value) {
    flashScanSuccess(t('inventory.memberInventory.alreadyHere', {name: item.name ?? ''}))
    return
  }
  await assignToCurrentMember(item)
}, {formatError: (e) => apiErrorMessage(e) ?? t('inventory.assign.errors.failed')})

async function handleScanAssign() {
  const term = scanValue.value.trim()
  if (!term) return
  scanValue.value = ''
  await runScanAssign(term)
  if (scanAssignError.value) flashScanError(scanAssignError.value)
}

async function onUnknownScanCreated(item: InventoryItem) {
  unknownScanCode.value = null
  try {
    await assignToCurrentMember(item)
  } catch (e) {
    flashScanError(apiErrorMessage(e) ?? t('inventory.assign.errors.failed'))
  }
}

const memberId = computed(() => Number(route.params.memberId))


const canManage = computed(() => hasPermission(StationPermission.INVENTORY_MANAGER))

const member = ref<StationMember | null>(null)
const items = ref<MyInventoryItem[]>([])

/**
 * Whose gear this is, at the head of the page. "Mitglieder-Inventar" stands above every one of them
 * and is what the tab, the history and a bookmark carry, so the person is what the page is called.
 * The plain wording stands while it loads and where the member could not be fetched.
 */
const pageTitle = computed(() => member.value?.name?.trim() || t('pages.inventory-member.title'))

interface InventoryGroup {
  inventoryId: number
  inventoryName: string
  items: MyInventoryItem[]
}

const grouped = computed((): InventoryGroup[] => {
  const byInv = new Map<number, MyInventoryItem[]>()
  for (const item of items.value) {
    const list = byInv.get(item.inventoryId) ?? []
    list.push(item)
    byInv.set(item.inventoryId, list)
  }
  const groups: InventoryGroup[] = []
  for (const [invId, invItems] of byInv) {
    groups.push({
      inventoryId: invId,
      inventoryName: invItems[0]?.inventoryName ?? '',
      items: invItems,
    })
  }
  return groups
})

const {loading, error, reload: loadData} = useAsyncLoader(async () => {
  const mid = memberId.value
  const [memberItems, allMembers] = await Promise.all([
    inventory.memberItems(mid),
    stationMembers.listMembers(),
  ])
  items.value = memberItems
  member.value = allMembers.find(m => m.id === mid) ?? null
})

function goBack() {
  router.push({name: routes.movements})
}

const showWizard = ref(false)
const wizardPrefill = ref<WizardPrefill>({})

/**
 * A swap of this member's piece, started the one way every swap is started.
 *
 * <p>The piece answers three of the wizard's questions, so it asks the fourth and shows the chain it
 * would walk before anything is written.
 */
function openExchangeModal(item: MyInventoryItem) {
  wizardPrefill.value = {
    purpose: MovementPurpose.EXCHANGE,
    memberId: memberId.value,
    itemId: item.id,
    inventoryId: item.inventoryId,
    oldSizeId: item.sizeId ?? null,
    skip: ['purpose', 'party', 'subject'],
  }
  showWizard.value = true
}

watch(memberId, loadData)
</script>

<template>
  <ViewContent
      :title="pageTitle"
      :subtitle="t('pages.inventory-member.subtitle')"
  >
    <div class="space-y-6">
      <MemberInventoryHeader :member="member" @back="goBack" />

      <Alert v-if="error" variant="error">{{ error }}</Alert>
      <ReturnEverythingBar v-if="canManage && items.length > 0" :member-id="memberId" @done="loadData"/>

      <AsyncSection :loading="loading">
        <MemberInventoryScanPanel
            v-if="canAssign"
            v-model:hand-out-mode="handOutMode"
            v-model:scan-value="scanValue"
            :scan-busy="scanBusy"
            :scan-error="scanError"
            :scan-success="scanSuccess"
            @submit="handleScanAssign"
            @decoded="onCameraScan"
        />

        <MemberInventoryGroups
            :groups="grouped"
            :items="items"
            :show-exchange-button="canManageInventory()"
            @request-exchange="openExchangeModal"
        />

        <MovementsPanel :member-id="memberId" @changed="loadData"/>
      </AsyncSection>

      <UnknownScanModal
          v-if="unknownScanCode"
          :scanned-code="unknownScanCode"
          context="member"
          @created="onUnknownScanCreated"
          @close="unknownScanCode = null"
      />

      <MovementWizard v-model="showWizard" :prefill="wizardPrefill" @started="loadData"/>
    </div>
  </ViewContent>
</template>
