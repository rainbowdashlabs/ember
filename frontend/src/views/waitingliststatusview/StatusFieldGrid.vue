/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import StatusPersonInfo from './StatusPersonInfo.vue'
import StatusPositionInfo from './StatusPositionInfo.vue'
import type { WaitingListPublicStatus } from '@/api/types'

const props = defineProps<{
  status: WaitingListPublicStatus
}>()
</script>

<template>
  <div class="grid gap-3 sm:grid-cols-2">
    <StatusPersonInfo :status="props.status" />
    <StatusPositionInfo :status="props.status" />
  </div>
</template>
