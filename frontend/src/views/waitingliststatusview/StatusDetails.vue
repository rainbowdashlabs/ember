/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import SectionHeader from '@/components/typography/SectionHeader.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import StatusFieldGrid from './StatusFieldGrid.vue'
import StatusFieldValues from './StatusFieldValues.vue'
import type { WaitingListPublicStatus } from '@/api/types'

const props = defineProps<{ status: WaitingListPublicStatus }>()
</script>

<template>
  <NeutralContainer class="space-y-4">
    <div class="text-center space-y-1">
      <SectionHeader class="text-lg font-semibold">{{ props.status.listName }}</SectionHeader>
    </div>
    <StatusFieldGrid :status="props.status" />
    <StatusFieldValues v-if="props.status.fields && props.status.fields.length > 0" :status="props.status" />
  </NeutralContainer>
</template>
