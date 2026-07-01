/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import SubHeader from '@/components/typography/SubHeader.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SizeBadge from '@/components/badge/SizeBadge.vue'
import StationBadge from '@/components/badge/StationBadge.vue'
import UserTagBadge from '@/components/badge/UserTagBadge.vue'
import WaitingListStatusBadge from '@/components/badge/WaitingListStatusBadge.vue'
import ExchangeStatusBadge from '@/views/stationview/inventory/exchangeview/ExchangeStatusBadge.vue'
import {ExchangeStatus, WaitingListEntryStatus} from '@/api/types'
</script>

<template>
  <section class="space-y-4">
    <SubHeader>Badges</SubHeader>
    <div class="flex flex-wrap gap-2 items-center">
      <PrimaryBadge>Primary</PrimaryBadge>
      <SecondaryBadge>Secondary</SecondaryBadge>
      <SuccessBadge>Success</SuccessBadge>
      <ErrorBadge>Error</ErrorBadge>
      <InfoBadge>Info</InfoBadge>
      <SizeBadge>M</SizeBadge>
      <SizeBadge lost>M (lost)</SizeBadge>
      <StationBadge station-name="DLRG Musterstadt"/>
      <UserTagBadge>Tag</UserTagBadge>
      <UserTagBadge color="#3694FF">Tag (colored)</UserTagBadge>
    </div>
    <SubHeader>Exchange Status</SubHeader>
    <div class="flex flex-wrap gap-2 items-center">
      <ExchangeStatusBadge :status="ExchangeStatus.ANNOUNCED"/>
      <ExchangeStatusBadge :status="ExchangeStatus.RECEIVED"/>
      <ExchangeStatusBadge :status="ExchangeStatus.SHIPPED"/>
      <ExchangeStatusBadge :status="ExchangeStatus.ARRIVED"/>
      <ExchangeStatusBadge :status="ExchangeStatus.DONE"/>
    </div>
    <SubHeader>Waiting List Status</SubHeader>
    <div class="flex flex-wrap gap-2 items-center">
      <WaitingListStatusBadge :status="WaitingListEntryStatus.PENDING"/>
      <WaitingListStatusBadge :status="WaitingListEntryStatus.WAITING"/>
      <WaitingListStatusBadge :status="WaitingListEntryStatus.INVITED"/>
      <WaitingListStatusBadge :status="WaitingListEntryStatus.TESTING"/>
      <WaitingListStatusBadge :status="WaitingListEntryStatus.JOINED"/>
      <WaitingListStatusBadge :status="WaitingListEntryStatus.WITHDRAWN"/>
    </div>
  </section>
</template>
