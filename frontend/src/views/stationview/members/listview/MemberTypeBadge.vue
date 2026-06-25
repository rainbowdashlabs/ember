/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {StationUserTypeLabels, type StationUserTypeName} from '@/api/types'

defineProps<{
  userType?: string | null
}>()
</script>

<template>
  <span
      v-if="userType"
      class="inline-block rounded-full px-2 py-0.5 text-[10px] font-medium"
      :class="{
        'bg-primary/10 text-primary': userType === 'TEAM' || userType === 'MANAGER',
        'bg-info/10 text-info-accent': userType === 'GUARDIAN',
        'bg-bg-light-accent dark:bg-bg-dark-accent text-(--text-muted)': userType === 'MEMBER' || userType === 'TRIAL',
      }"
  >{{ StationUserTypeLabels[userType as StationUserTypeName] ?? userType }}</span>
</template>
