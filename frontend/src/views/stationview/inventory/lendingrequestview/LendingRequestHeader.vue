/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import SubHeader from '@/components/typography/SubHeader.vue'
import type {LendingRequestDetail} from '@/api/generated/schema'
import LendingStatusBadge from './LendingStatusBadge.vue'

/** The other station of a request and where the request stands. */
defineProps<{
  detail: LendingRequestDetail
}>()
</script>

<template>
  <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-2 mb-4">
    <SubHeader>
      {{ detail.request.isOwner ? detail.request.requestingStationName : detail.request.owningStationName }}
    </SubHeader>
    <LendingStatusBadge :status="detail.request.request.status" :overdue="detail.request.overdue"/>
  </div>
</template>
