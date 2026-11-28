/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import HelpPermissionGuard from '@/components/helpcenter/HelpPermissionGuard.vue'
import {StationPermission} from '@/api/generated/schema'
import {provideInventoryRoutes, STATION_INVENTORY_ROUTES} from '@/composables/useInventoryRoutes'
import BorrowColumn from '@/views/stationview/inventory/lendingoverviewview/BorrowColumn.vue'
import LendColumn from '@/views/stationview/inventory/lendingoverviewview/LendColumn.vue'
import RequestsTable from '@/views/stationview/inventory/lendingoverviewview/RequestsTable.vue'
import {borrowBlocks, lendBlocks, type BorrowedGroup} from '@/views/stationview/inventory/lendingoverviewview/overviewBlocks'
import {useRequestsTable} from '@/views/stationview/inventory/lendingoverviewview/requestsTable'
import {incomingRequests, outgoingRequests} from './lendinghelp/fixtures'

const {t} = useI18n()
provideInventoryRoutes({...STATION_INVENTORY_ROUTES, lendingRequest: undefined})

/** The day the sample requests are read on, so the blocks stay the same whenever the page is read. */
const SAMPLE_DAY = '2026-06-01'

const requests = ref([...incomingRequests, ...outgoingRequests])
const lend = lendBlocks(requests.value, SAMPLE_DAY)
const borrow = borrowBlocks(requests.value, SAMPLE_DAY)
const borrowed: BorrowedGroup[] = [
  {requestId: 7, stationName: 'DLRG Musterstadt', dueOn: '2026-06-04', pieces: [{name: 'Schwimmweste', internalId: 'SW-01'}, {name: 'Schwimmweste', internalId: 'SW-02'}]},
]
const table = useRequestsTable(requests)
</script>

<template>
  <HelpArticle :title="t('helpCenter.inventoryLending.title')" :subtitle="t('helpCenter.inventoryLending.subtitle')">
    <HelpSection :title="t('helpCenter.inventoryLending.whatIs')">
      <p>{{ t('helpCenter.inventoryLending.whatIsText') }}</p>
      <p>{{ t('helpCenter.inventoryLending.whatIsText2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryLending.borrowTitle')">
      <p>{{ t('helpCenter.inventoryLending.borrowText') }}</p>
      <p>{{ t('helpCenter.inventoryLending.blocksText') }}</p>
      <NeutralContainer>
        <BorrowColumn :blocks="borrow" :borrowed="borrowed" reads/>
      </NeutralContainer>
    </HelpSection>

    <HelpPermissionGuard :permissions="[StationPermission.INVENTORY_LENDING_MANAGER]" :label="t('helpCenter.permissionLabel.inventoryLending')">
      <HelpSection :title="t('helpCenter.inventoryLending.lend')">
        <p>{{ t('helpCenter.inventoryLending.lendText') }}</p>
        <NeutralContainer>
          <LendColumn :blocks="lend"/>
        </NeutralContainer>
      </HelpSection>
    </HelpPermissionGuard>

    <HelpSection :title="t('helpCenter.inventoryLending.borrowedTitle')">
      <p>{{ t('helpCenter.inventoryLending.borrowedText') }}</p>
      <p>{{ t('helpCenter.inventoryLending.borrowedSnapshot') }}</p>
      <p>{{ t('helpCenter.inventoryLending.borrowedReturn') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryLending.tableTitle')">
      <p>{{ t('helpCenter.inventoryLending.tableText') }}</p>
      <RequestsTable :table="table"/>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryLending.otherInstanceTitle')">
      <p>{{ t('helpCenter.inventoryLending.otherInstanceText') }}</p>
      <p>{{ t('helpCenter.inventoryLending.otherInstanceText2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryLending.statusTitle')">
      <p>{{ t('helpCenter.inventoryLending.statusRequestedDesc') }}</p>
      <p>{{ t('helpCenter.inventoryLending.statusApprovedDesc') }}</p>
      <p>{{ t('helpCenter.inventoryLending.statusLentDesc') }}</p>
      <p>{{ t('helpCenter.inventoryLending.statusReturnedDesc') }}</p>
      <p>{{ t('helpCenter.inventoryLending.statusDeclinedDesc') }}</p>
      <p>{{ t('helpCenter.inventoryLending.statusClosedDesc') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.inventoryLending.tip') }}</HelpTip>
  </HelpArticle>
</template>
