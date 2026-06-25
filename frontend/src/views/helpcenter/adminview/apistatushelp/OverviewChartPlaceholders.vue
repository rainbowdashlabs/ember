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
  <div class="grid grid-cols-1 lg:grid-cols-2 gap-4 mb-4">
    <NeutralContainer>
      <p class="text-sm font-semibold mb-2">{{ t('apiStatus.requestsOverTime') }}</p>
      <div class="h-24 flex items-center justify-center text-(--text-muted)">
        <font-awesome-icon :icon="['fas', 'chart-line']" class="text-2xl" />
      </div>
    </NeutralContainer>
    <NeutralContainer>
      <p class="text-sm font-semibold mb-2">{{ t('apiStatus.avgResponseTime') }}</p>
      <div class="h-24 flex items-center justify-center text-(--text-muted)">
        <font-awesome-icon :icon="['fas', 'chart-line']" class="text-2xl" />
      </div>
    </NeutralContainer>
  </div>
</template>
