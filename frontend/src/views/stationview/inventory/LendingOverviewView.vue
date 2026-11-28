/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import AsyncSection from '@/components/feedback/AsyncSection.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import {inventory} from '@/api'
import * as lending from '@/api/lending'
import {StationPermission, type BorrowedItemResponse, type LendingRequestResponse} from '@/api/generated/schema'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useSession} from '@/composables/useSession'
import {useSidebarCounts} from '@/composables/useSidebarCounts'
import {todayIsoDate} from '@/util/format'
import DeclineRequestModal from './lendingrequestview/DeclineRequestModal.vue'
import BorrowColumn from './lendingoverviewview/BorrowColumn.vue'
import LendColumn from './lendingoverviewview/LendColumn.vue'
import RequestsTable from './lendingoverviewview/RequestsTable.vue'
import {borrowBlocks, borrowedGroups, lendBlocks} from './lendingoverviewview/overviewBlocks'
import {useRequestsTable} from './lendingoverviewview/requestsTable'

/**
 * The start of the lending section: what the station lends and what it borrows side by side, each
 * sorted into the next step it needs, and every request below in one table. Lending managers see
 * both sides; whoever may only ask for gear sees what the station borrows; whoever may only read the
 * inventory sees the partner gear at the station.
 */
const {t} = useI18n()
const {loaded, hasPermission} = useSession()
const {refresh: refreshCounts} = useSidebarCounts()

const lends = computed(() => hasPermission(StationPermission.INVENTORY_LENDING_MANAGER))
const asks = computed(() => lends.value || hasPermission(StationPermission.INVENTORY_LENDING_REQUEST))
const reads = computed(() => hasPermission(StationPermission.INVENTORY_READ))

const requests = ref<LendingRequestResponse[]>([])
const borrowed = ref<BorrowedItemResponse[]>([])

const {loading, failure, reload} = useAsyncLoader(async (isCurrent) => {
  if (!loaded.value) return
  const [answered, here] = await Promise.all([
    asks.value ? lending.listRequests() : Promise.resolve([]),
    reads.value ? inventory.listBorrowed() : Promise.resolve([]),
  ])
  if (!isCurrent()) return
  requests.value = answered
  borrowed.value = here
})
watch(loaded, value => { if (value) void reload() })

const today = todayIsoDate()
const lend = computed(() => lends.value ? lendBlocks(requests.value, today) : null)
const borrow = computed(() => asks.value ? borrowBlocks(requests.value, today) : null)
const borrowedHere = computed(() => borrowedGroups(borrowed.value))

const table = useRequestsTable(requests)

const declining = ref<number | null>(null)
const declineOpen = computed({
  get: () => declining.value !== null,
  set: (value: boolean) => { if (!value) declining.value = null },
})

const {run: answer, running: answering, failure: answerFailure} = useAsyncAction(async (step: () => Promise<unknown>) => {
  await step()
  await reload()
  void refreshCounts()
})

function approve(requestId: number) {
  void answer(() => lending.approveRequest(requestId))
}

function decline(reason: string) {
  const requestId = declining.value
  if (requestId === null) return
  void answer(async () => {
    await lending.declineRequest(requestId, reason)
    declining.value = null
  })
}
</script>

<template>
  <ViewContent :title="t('pages.inventory-lending.title')" :subtitle="t('pages.inventory-lending.subtitle')">
    <FailureAlert v-if="!declineOpen" :failure="answerFailure" class="mb-4"/>
    <AsyncSection :failure="failure" :loading="loading">
      <div class="space-y-8">
        <div class="grid grid-cols-1 gap-8" :class="lend ? 'lg:grid-cols-2' : ''">
          <LendColumn v-if="lend" :blocks="lend" :busy="answering" @approve="approve" @decline="declining = $event"/>
          <BorrowColumn :blocks="borrow" :borrowed="borrowedHere" :reads="reads"/>
        </div>
        <section v-if="asks" class="space-y-2">
          <SectionHeader>{{ t('lendingOverview.allRequests') }}</SectionHeader>
          <RequestsTable :table="table"/>
        </section>
      </div>
    </AsyncSection>
    <DeclineRequestModal v-model="declineOpen" :busy="answering" :failure="answerFailure" @decline="decline"/>
  </ViewContent>
</template>
