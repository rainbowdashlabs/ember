/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import AsyncSection from '@/components/feedback/AsyncSection.vue'
import type {Inventory} from '@/api/inventory'
import type {ProcurementEntry} from '@/api/procurement'
import {StationPermission, type StationMember} from '@/api/types'
import { procurement, inventory, stationMembers } from '@/api'
import { useSession } from '@/composables/useSession'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import { useInventoryRoutes } from '@/composables/useInventoryRoutes'
import { describeFailure } from '@/util/failure'
import ProcurementEntryRow from './procurementview/ProcurementEntryRow.vue'
import ProcurementCreateModal from './procurementview/ProcurementCreateModal.vue'

const { t } = useI18n()
const routes = useInventoryRoutes()
const { hasPermission } = useSession()
const canManageProcurement = computed(() => hasPermission(StationPermission.INVENTORY_PROCUREMENT))

const entries = ref<ProcurementEntry[]>([])
const inventories = ref<Inventory[]>([])
const members = ref<StationMember[]>([])

const showCreateModal = ref(false)

/**
 * The orders, what may be ordered, and who they can be for.
 *
 * <p>An entry can name the person it is for, and an association orders for a station rather than for
 * anybody, so it neither offers the choice nor is allowed to ask who there is.
 */
const {loading, failure} = useAsyncLoader(async () => {
  const [e, inv, m] = await Promise.all([
    procurement.listProcurement(),
    inventory.listInventories(),
    routes.member ? stationMembers.listMembers().catch(() => []) : Promise.resolve([]),
  ])
  entries.value = e
  inventories.value = inv
  members.value = m
})

/**
 * What may be ordered more of: only an inventory holding one thing in many copies.
 *
 * "Three more" needs something to be three more of, which a drawer holding a laminator and a toy
 * fire engine does not have, so it is not offered rather than being offered and then refused.
 */
const orderableInventories = computed(() => inventories.value.filter(i => i.homogeneous))

/**
 * Fetches the orders again after one was settled or struck out, and says a stale screen rather than a
 * failed change: the change is written by then, and a reader told otherwise makes it twice.
 */
async function reloadEntries() {
  try {
    entries.value = await procurement.listProcurement()
  } catch (e) {
    failure.value = {...describeFailure(e, t), message: t('failure.staleAfterAction')}
  }
}

async function fulfillEntry(id: number) {
  failure.value = null
  try {
    await procurement.fulfill(id)
  } catch (e) {
    failure.value = describeFailure(e, t)
    return
  }
  await reloadEntries()
}

async function deleteEntry(id: number) {
  failure.value = null
  try {
    await procurement.deleteProcurement(id)
  } catch (e) {
    failure.value = describeFailure(e, t)
    return
  }
  await reloadEntries()
}

function onCreated() {
  void reloadEntries()
}

</script>

<template>
  <ViewContent
      :title="t('pages.inventory-procurement.title')"
      :subtitle="t('pages.inventory-procurement.subtitle')"
  >
    <slot name="before"/>

    <div class="space-y-6">
      <div class="flex items-center justify-end">
        <PrimaryButton
            v-if="canManageProcurement"
            :icon="['fas', 'plus']"
            data-testid="procurement-create"
            @click="showCreateModal = true"
        >
          {{ t('procurement.create') }}
        </PrimaryButton>
      </div>

      <AsyncSection
        :empty="entries.length === 0"
        :empty-message="t('procurement.empty')"
        :failure="failure"
        :loading="loading"
      >
        <div class="space-y-3">
          <ProcurementEntryRow
            v-for="entry in entries"
            :key="entry.id"
            :entry="entry"
            :can-manage-procurement="canManageProcurement"
            @fulfill="fulfillEntry"
            @delete="deleteEntry"
          />
        </div>
      </AsyncSection>

      <ProcurementCreateModal
        v-model="showCreateModal"
        :inventories="orderableInventories"
        :members="members"
        @created="onCreated"
      />
    </div>
  </ViewContent>
</template>
