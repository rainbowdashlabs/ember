/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import MutedIcon from '@/components/display/MutedIcon.vue'

const {t} = useI18n()
</script>

<template>
  <div class="rounded-lg px-4 py-3 bg-bg-light-accent/20 dark:bg-bg-dark-accent/20 space-y-2">
    <div class="flex items-center justify-between flex-wrap gap-2">
      <div class="flex items-center gap-2 flex-wrap">
        <span class="font-semibold text-sm">Kleidergröße</span>
        <span class="text-xs text-(--text-muted)">13.05.2026, 10:00</span>
      </div>
      <SuccessBadge>
        <font-awesome-icon :icon="['fas', 'check']" class="mr-1"/>
        {{ t('memberDetail.acknowledged') }}
      </SuccessBadge>
    </div>
    <div class="flex items-center gap-2 text-xs">
      <span class="text-(--text-muted)">S</span>
      <MutedIcon :icon="['fas', 'chevron-right']"/>
      <span class="font-medium">M</span>
    </div>
    <div class="text-xs text-(--text-muted)">
      <div class="flex items-center gap-1">
        <font-awesome-icon :icon="['fas', 'check']" class="h-3 w-3 text-success"/>
        <span>Anna Schmidt (13.05.2026, 14:15)</span>
      </div>
    </div>
  </div>
</template>
