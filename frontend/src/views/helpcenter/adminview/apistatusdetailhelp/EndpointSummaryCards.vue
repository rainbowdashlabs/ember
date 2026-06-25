/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'

const {t} = useI18n()
</script>

<template>
  <div class="grid grid-cols-2 gap-3 mb-4">
    <NeutralContainer class="text-center">
      <p class="text-2xl font-bold">1.240</p>
      <p class="text-xs text-(--text-muted)">{{ t('apiStatus.totalRequests') }}</p>
    </NeutralContainer>
    <NeutralContainer class="text-center">
      <p class="text-2xl font-bold">12ms</p>
      <p class="text-xs text-(--text-muted)">{{ t('apiStatus.avgResponseTime') }}</p>
    </NeutralContainer>
  </div>
</template>
