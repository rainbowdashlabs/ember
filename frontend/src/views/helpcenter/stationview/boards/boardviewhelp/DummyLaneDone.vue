/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import UserAvatar from '@/components/avatar/UserAvatar.vue'

const {t} = useI18n()
</script>

<template>
  <div class="md:flex-1 md:min-w-[14rem] md:max-w-[24rem] bg-bg-light-accent dark:bg-bg-dark-accent border border-[var(--border)] rounded-lg p-3 border-t-2" style="border-top-color: #00C507">
    <div class="flex items-center justify-between mb-3">
      <SubHeader class="text-sm text-[var(--text-muted)] uppercase tracking-wide">Erledigt</SubHeader>
      <BaseBadge bg-class="bg-[var(--bg)]" class="text-[var(--text-muted)]">1</BaseBadge>
    </div>
    <div class="space-y-2">
      <div class="bg-[var(--bg)] border border-[var(--border)] rounded-lg p-3 shadow-sm">
        <p class="text-sm font-medium leading-tight mb-1">Übungsplan Mai abschließen</p>
        <div class="flex items-center gap-2 mb-2">
          <div class="flex-1 h-1.5 bg-[var(--bg-muted)] rounded-full overflow-hidden">
            <div class="h-full rounded-full bg-green-500" style="width: 100%" />
          </div>
          <span class="text-xs text-[var(--text-muted)] whitespace-nowrap">4/4</span>
        </div>
        <div class="flex items-center justify-between text-xs text-[var(--text-muted)]">
          <div class="flex items-center gap-2">
            <font-awesome-icon :icon="['fas', 'angle-down']" class="text-blue-400 text-xs" />
          </div>
          <div class="flex items-center gap-2">
            <span class="font-mono opacity-60">PLAN-3</span>
            <UserAvatar :identity="{stationUid: '', memberUid: ''}" name="Max M." size="sm" />
          </div>
        </div>
      </div>
    </div>
    <div class="mt-2">
      <SecondaryButton class="w-full text-xs">
        3 {{ t('boards.archived') }}
      </SecondaryButton>
    </div>
  </div>
</template>
