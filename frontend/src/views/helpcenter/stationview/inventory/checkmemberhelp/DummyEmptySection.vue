/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3">
    <div class="flex items-center justify-between gap-2">
      <SubHeader>Stiefel</SubHeader>
      <span class="text-sm text-(--text-muted) shrink-0">0 / 1</span>
    </div>

    <div class="rounded border border-bg-light-accent/50 dark:border-bg-dark-accent/50 p-3 border-dashed">
      <div class="text-sm text-(--text-muted)">{{ t('helpCenter.inventoryCheckMember.emptySlotText') }}</div>
    </div>
  </NeutralContainer>
</template>
