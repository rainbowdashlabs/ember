/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import type { WaitingListInviteInfo } from '@/api/types'

const props = defineProps<{ inviteInfo: WaitingListInviteInfo }>()
</script>

<template>
  <NeutralContainer class="text-center space-y-2">
    <SectionHeader class="text-lg font-semibold">{{ props.inviteInfo.listName }}</SectionHeader>
    <p v-if="props.inviteInfo.listDescription" class="text-sm text-(--text-muted)">{{ props.inviteInfo.listDescription }}</p>
  </NeutralContainer>
</template>
