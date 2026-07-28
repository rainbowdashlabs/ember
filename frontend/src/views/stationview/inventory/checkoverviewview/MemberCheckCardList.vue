/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import type {MemberCheckSummary} from '@/api/inventoryCheck'
import MemberCheckCard from './MemberCheckCard.vue'

const props = defineProps<{
  members: MemberCheckSummary[]
  currentMemberId: number | undefined
}>()

const emit = defineEmits<{
  (e: 'start-check', memberId: number): void
  (e: 'view-last-check', member: MemberCheckSummary): void
}>()
</script>

<template>
  <div class="sm:hidden space-y-2">
    <MemberCheckCard
        v-for="member in members"
        :key="member.memberId"
        :member="member"
        :current-member-id="currentMemberId"
        @start-check="emit('start-check', $event)"
        @view-last-check="emit('view-last-check', $event)"
    />
  </div>
</template>
