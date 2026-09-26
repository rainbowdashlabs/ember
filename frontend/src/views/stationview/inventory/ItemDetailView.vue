/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import {inventory, inventoryContainers, stationMembers} from '@/api'
import {ItemOwner} from '@/api/inventory'
import type {InventoryItem, InventoryItemHistory, InventorySize} from '@/api/inventory'
import type {ItemCheckHistoryEntry, ItemLocationResponse} from '@/api/inventoryContainers'
import {StationPermission, type StationMember} from '@/api/types'
import {useSession} from '@/composables/useSession'
import {useActsForOwner} from '@/composables/useActsForOwner'
import {useInventoryRoutes} from '@/composables/useInventoryRoutes'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useFlashMessage} from '@/composables/useFlashMessage'
import {describeFailure, type Failure} from '@/util/failure'
import ItemPanels from './itemdetailview/ItemPanels.vue'
import AssignItemModal from './itemdetailview/AssignItemModal.vue'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const {hasPermission} = useSession()
const routes = useInventoryRoutes()
const canEdit = computed(() => hasPermission(StationPermission.INVENTORY_EDIT))

const itemId = computed(() => Number(route.params.id))
const item = ref<InventoryItem | null>(null)
const historyEntries = ref<InventoryItemHistory[]>([])
const checkHistory = ref<ItemCheckHistoryEntry[]>([])
const sizes = ref<InventorySize[]>([])
const members = ref<StationMember[]>([])
const location = ref<ItemLocationResponse | null>(null)
const {message: success, flash} = useFlashMessage(3000)

const isManager = computed(() => hasPermission('INVENTORY_MANAGER') || hasPermission('STATION_ADMINISTRATOR'))

/**
 * The piece by what it is called, at the head of the page, because "Item-Details" stands above
 * every one of them and is what the tab, the history and a bookmark carry. Where nobody named it,
 * what is written on it is its name; the plain wording stands until it has arrived and where it
 * could not be fetched at all.
 */
const pageTitle = computed(() =>
    item.value?.name || item.value?.internalId || t('pages.inventory-item-detail.title'))

/**
 * Whether this station may describe this piece of gear, which is not the same as being allowed to
 * describe gear.
 *
 * <p>Gear belonging to an association that runs on this instance is described by that association,
 * and the server refuses the station either way. Showing a live form and refusing the save afterwards
 * is the same refusal delivered late, so the form is simply not offered.
 *
 * <p>Gear kept for a body that does not use Ember is the exception, and deliberately so: nobody else
 * could ever correct the record, so the station keeps the pen.
 *
 * <p>The association reading its own screens is looking at the same page, and for it nothing is owned
 * elsewhere: it is the owner, so it keeps the pencil and is offered none of what a station is offered
 * for somebody else's gear.
 */
const actsForOwner = useActsForOwner()
const ownedElsewhere = computed(() =>
    item.value?.ownerKind === ItemOwner.CLUSTER
    && item.value?.ownerClusterId != null
    && !actsForOwner.value)
const canEditItem = computed(() => (canEdit.value || isManager.value) && !ownedElsewhere.value)

/**
 * Handing it out, shelving it, reporting it missing and finding it again.
 *
 * <p>Ownership has nothing to say about these, because they are facts about where the thing is rather than
 * the owner's account of what it is. A station holding an association's jacket hands it to a member and
 * reports it missing exactly as it does with its own, and the server allows all of it.
 */
const canActOnItem = computed(() => canEdit.value || isManager.value)

const showAssignModal = ref(false)

const {loading, failure} = useAsyncLoader(async () => {
  const [i, h, ch] = await Promise.all([
    inventory.getItem(itemId.value),
    inventory.getItemHistory(itemId.value),
    inventoryContainers.listItemCheckHistory(itemId.value).catch(() => []),
  ])
  item.value = i
  historyEntries.value = h
  checkHistory.value = ch
  // The member list fills the assign modal, and an association has neither: its own station has nobody
  // to hand gear to and it may not read anybody else's roster. Asking and being refused is fine; letting
  // the refusal blank a page that is mostly about the piece itself is not.
  const [s, m, loc] = await Promise.all([
    inventory.listSizes(i.inventoryId),
    stationMembers.listMembers().catch(() => []),
    inventoryContainers.getItemLocation(itemId.value).catch(() => null),
  ])
  sizes.value = s
  members.value = m
  location.value = loc
})

function onError(reported: Failure) {
  failure.value = reported
}

function onUpdated(updated: InventoryItem) {
  item.value = updated
  failure.value = null
}

/** Reads the piece again, for when something started elsewhere has changed where it stands. */
async function reloadItem() {
  try {
    item.value = await inventory.getItem(itemId.value)
    flash(t('itemDetail.movementStarted'))
  } catch (e) {
    failure.value = describeFailure(e, t)
  }
}

/**
 * Reads the handover list again after the piece changed hands.
 *
 * <p>Caught apart from the change itself, because by the time this runs the piece has already moved.
 * Saying that handing it over failed would send the reader to do it a second time.
 */
async function refreshHistory() {
  try {
    historyEntries.value = await inventory.getItemHistory(itemId.value)
  } catch (e) {
    failure.value = {...describeFailure(e, t), message: t('failure.staleAfterAction')}
  }
}

async function doAssign(memberId: number) {
  failure.value = null
  try {
    const member = members.value.find(m => m.id === memberId)
    item.value = await inventory.assignItem(itemId.value, {
      memberId,
      memberName: member?.name ?? '',
    })
  } catch (e) {
    failure.value = describeFailure(e, t)
    return
  }
  showAssignModal.value = false
  flash(t('itemDetail.assigned'))
  await refreshHistory()
}

async function doUnassign() {
  failure.value = null
  try {
    item.value = await inventory.assignItem(itemId.value, {memberId: null})
  } catch (e) {
    failure.value = describeFailure(e, t)
    return
  }
  flash(t('itemDetail.unassigned'))
  await refreshHistory()
}

async function doMarkLost() {
  failure.value = null
  try {
    item.value = await inventory.markLost(itemId.value)
    flash(t('itemDetail.markedLost'))
  } catch (e) {
    failure.value = describeFailure(e, t)
  }
}

async function doMarkFound() {
  failure.value = null
  try {
    item.value = await inventory.markFound(itemId.value)
    flash(t('itemDetail.markedFound'))
  } catch (e) {
    failure.value = describeFailure(e, t)
  }
}
</script>

<template>
  <ViewContent
      :title="pageTitle"
      :subtitle="t('pages.inventory-item-detail.subtitle')"
  >
    <div class="space-y-6">
      <div class="flex items-center gap-2">
        <SecondaryButton :icon="['fas', 'arrow-left']" @click="router.back()"/>
        <SectionHeader>{{ t('itemDetail.title') }}</SectionHeader>
      </div>

      <Spinner v-if="loading" size="lg"/>
      <FailureAlert :failure="failure"/>
      <Alert v-if="success" variant="success">{{ success }}</Alert>

      <ItemPanels
        v-if="!loading && item"
        :item="item"
        :item-id="itemId"
        :sizes="sizes"
        :members="members"
        :location="location"
        :history-entries="historyEntries"
        :check-history="checkHistory"
        :can-edit-item="canEditItem"
        :can-act-on-item="canActOnItem"
        :can-assign="!!routes.member"
        :owned-elsewhere="ownedElsewhere"
        :is-manager="isManager"
        @updated="onUpdated"
        @error="onError"
        @reload="reloadItem"
        @assign="showAssignModal = true"
        @unassign="doUnassign"
        @mark-lost="doMarkLost"
        @mark-found="doMarkFound"
      />

      <AssignItemModal
        v-model="showAssignModal"
        :members="members"
        @assign="doAssign"
      />
    </div>
  </ViewContent>
</template>
