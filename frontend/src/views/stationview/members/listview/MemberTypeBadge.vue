/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import type {StationUserType} from '@/api/generated/schema'
import {StationUserTypeLabels} from '@/api/types'
import PillBadge from '@/components/badge/PillBadge.vue'

defineProps<{
  userType?: string | null
}>()
</script>

<template>
  <PillBadge
      v-if="userType"
      class="px-2 py-0.5 text-[10px] font-medium"
      :class="{
        'bg-primary/10 text-primary': userType === 'TEAM' || userType === 'MANAGER',
        'bg-info/10 text-info-accent': userType === 'GUARDIAN',
        'bg-bg-light-accent dark:bg-bg-dark-accent text-(--text-muted)': userType === 'MEMBER' || userType === 'TRIAL',
      }"
  >{{ StationUserTypeLabels[userType as StationUserType] ?? userType }}</PillBadge>
</template>
