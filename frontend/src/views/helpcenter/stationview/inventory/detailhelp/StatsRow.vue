/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import StatValue from '@/components/typography/StatValue.vue'

const {t} = useI18n()
</script>

<template>
  <div class="grid grid-cols-2 sm:grid-cols-5 gap-3">
    <NeutralContainer class="text-center">
      <div class="text-2xl font-bold">24</div>
      <div class="text-xs text-(--text-muted)">{{ t('inventory.detail.total') }}</div>
    </NeutralContainer>
    <NeutralContainer class="text-center">
      <StatValue color="success">8</StatValue>
      <div class="text-xs text-(--text-muted)">{{ t('inventory.detail.free') }}</div>
    </NeutralContainer>
    <NeutralContainer class="text-center">
      <StatValue>13</StatValue>
      <div class="text-xs text-(--text-muted)">{{ t('inventory.detail.assigned') }}</div>
    </NeutralContainer>
    <NeutralContainer class="text-center">
      <StatValue color="error">1</StatValue>
      <div class="text-xs text-(--text-muted)">{{ t('inventory.detail.lost') }}</div>
    </NeutralContainer>
    <NeutralContainer class="text-center">
      <div class="text-2xl font-bold text-secondary-accent">2</div>
      <div class="text-xs text-(--text-muted)">{{ t('helpCenter.inventoryDetail.lentOut') }}</div>
    </NeutralContainer>
  </div>
</template>
