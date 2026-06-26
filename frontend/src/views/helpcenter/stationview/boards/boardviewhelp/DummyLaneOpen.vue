/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import SubHeader from '@/components/typography/SubHeader.vue'
import UserAvatar from '@/components/avatar/UserAvatar.vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>

<template>
  <div class="md:flex-1 md:min-w-[14rem] md:max-w-[24rem] bg-bg-light-accent dark:bg-bg-dark-accent border border-[var(--border)] rounded-lg p-3 border-t-2" style="border-top-color: var(--primary)">
    <div class="flex items-center justify-between mb-3">
      <SubHeader class="text-sm text-[var(--text-muted)] uppercase tracking-wide">{{ t('helpCenter.boardsLaneOpen.laneTitle') }}</SubHeader>
      <BaseBadge bg-class="bg-[var(--bg)]" class="text-[var(--text-muted)]">2</BaseBadge>
    </div>
    <div class="space-y-2">
      <div class="bg-[var(--bg)] border border-[var(--border)] rounded-lg p-3 shadow-sm">
        <p class="text-sm font-medium leading-tight mb-1">{{ t('helpCenter.boardsLaneOpen.task1Title') }}</p>
        <div class="flex flex-wrap gap-1 mb-2">
          <BaseBadge bg-class="bg-blue-500" class="text-white">{{ t('helpCenter.boardsLaneOpen.tagPlanning') }}</BaseBadge>
        </div>
        <div class="flex items-center justify-between text-xs text-[var(--text-muted)]">
          <div class="flex items-center gap-2">
            <font-awesome-icon :icon="['fas', 'angle-up']" class="text-orange-500 text-xs" />
            <span class="flex items-center gap-0.5">
              <font-awesome-icon :icon="['fas', 'calendar']" class="text-[0.6rem]" />
              15.06
            </span>
          </div>
          <div class="flex items-center gap-2">
            <span class="font-mono opacity-60">PLAN-7</span>
            <UserAvatar :identity="{stationUid: '', memberUid: ''}" :name="t('helpCenter.boardsLaneOpen.memberName')" size="sm" />
          </div>
        </div>
      </div>
      <div class="bg-[var(--bg)] border border-[var(--border)] rounded-lg p-3 shadow-sm">
        <p class="text-sm font-medium leading-tight mb-1">{{ t('helpCenter.boardsLaneOpen.task2Title') }}</p>
        <div class="flex items-center justify-between text-xs text-[var(--text-muted)]">
          <div class="flex items-center gap-2">
            <font-awesome-icon :icon="['fas', 'equals']" class="text-yellow-500 text-xs" />
          </div>
          <div class="flex items-center gap-2">
            <span class="font-mono opacity-60">PLAN-8</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
