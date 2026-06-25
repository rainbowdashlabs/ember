/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import SubHeader from '@/components/typography/SubHeader.vue'
import UserAvatar from '@/components/avatar/UserAvatar.vue'
</script>

<template>
  <div class="md:flex-1 md:min-w-[14rem] md:max-w-[24rem] bg-bg-light-accent dark:bg-bg-dark-accent border border-[var(--border)] rounded-lg p-3 border-t-2" style="border-top-color: #3694FF">
    <div class="flex items-center justify-between mb-3">
      <SubHeader class="text-sm text-[var(--text-muted)] uppercase tracking-wide">In Arbeit</SubHeader>
      <BaseBadge bg-class="bg-[var(--bg)]" class="text-[var(--text-muted)]">1</BaseBadge>
    </div>
    <div class="space-y-2">
      <div class="bg-[var(--bg)] border border-[var(--border)] rounded-lg p-3 shadow-sm">
        <p class="text-sm font-medium leading-tight mb-1">Fahrzeugcheck organisieren</p>
        <div class="flex flex-wrap gap-1 mb-2">
          <BaseBadge bg-class="bg-green-600" class="text-white">Wartung</BaseBadge>
          <BaseBadge bg-class="bg-orange-500" class="text-white">Dringend</BaseBadge>
        </div>
        <div class="flex items-center gap-2 mb-2">
          <div class="flex-1 h-1.5 bg-[var(--bg-muted)] rounded-full overflow-hidden">
            <div class="h-full rounded-full bg-primary" style="width: 66%" />
          </div>
          <span class="text-xs text-[var(--text-muted)] whitespace-nowrap">2/3</span>
        </div>
        <div class="flex items-center justify-between text-xs text-[var(--text-muted)]">
          <div class="flex items-center gap-2">
            <div class="flex gap-0.5">
              <span class="w-1.5 h-1.5 rounded-full bg-green-500" />
              <span class="w-1.5 h-1.5 rounded-full bg-green-500" />
            </div>
            <font-awesome-icon :icon="['fas', 'angles-up']" class="text-red-500 text-xs" />
            <span class="flex items-center gap-0.5 text-red-500 font-medium">
              <font-awesome-icon :icon="['fas', 'calendar']" class="text-[0.6rem]" />
              03.06
            </span>
            <span class="flex items-center gap-0.5">
              <font-awesome-icon :icon="['fas', 'paperclip']" class="text-[0.6rem]" />
              2
            </span>
          </div>
          <div class="flex items-center gap-2">
            <span class="font-mono opacity-60">PLAN-5</span>
            <UserAvatar :identity="{stationUid: '', memberUid: ''}" name="Erika F." size="sm" />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
