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
import type {
  ProcurementEntry,
  Inventory,
  StationMember,
} from '@/api/types'
import { StationPermission } from '@/api/types'
import { procurement, inventory, stationMembers } from '@/api'
import { useSession } from '@/composables/useSession'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import ProcurementEntryRow from './procurementview/ProcurementEntryRow.vue'
import ProcurementCreateModal from './procurementview/ProcurementCreateModal.vue'

const { t } = useI18n()
const { hasPermission } = useSession()
const canManageProcurement = computed(() => hasPermission(StationPermission.INVENTORY_PROCUREMENT))

const entries = ref<ProcurementEntry[]>([])
const inventories = ref<Inventory[]>([])
const members = ref<StationMember[]>([])

const showCreateModal = ref(false)

const {loading, error} = useAsyncLoader(async () => {
  const [e, inv, m] = await Promise.all([
    procurement.listProcurement(),
    inventory.listInventories(),
    stationMembers.listMembers(),
  ])
  entries.value = e
  inventories.value = inv
  members.value = m
})

async function reloadEntries() {
  try {
    entries.value = await procurement.listProcurement()
  } catch {
    error.value = t('common.error')
  }
}

async function fulfillEntry(id: number) {
  error.value = ''
  try {
    await procurement.fulfill(id)
    await reloadEntries()
  } catch {
    error.value = t('common.error')
  }
}

async function deleteEntry(id: number) {
  error.value = ''
  try {
    await procurement.deleteProcurement(id)
    await reloadEntries()
  } catch {
    error.value = t('common.error')
  }
}

function onCreated() {
  void reloadEntries()
}

function onCreateError() {
  error.value = t('common.error')
}
</script>

<template>
  <ViewContent
      :title="t('pages.inventory-procurement.title')"
      :subtitle="t('pages.inventory-procurement.subtitle')"
  >
    <div class="space-y-6">
      <div class="flex items-center justify-end">
        <PrimaryButton v-if="canManageProcurement" :icon="['fas', 'plus']" @click="showCreateModal = true">
          {{ t('procurement.create') }}
        </PrimaryButton>
      </div>

      <AsyncSection
        :empty="entries.length === 0"
        :empty-message="t('procurement.empty')"
        :error="error"
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
        :inventories="inventories"
        :members="members"
        @created="onCreated"
        @error="onCreateError"
      />
    </div>
  </ViewContent>
</template>
