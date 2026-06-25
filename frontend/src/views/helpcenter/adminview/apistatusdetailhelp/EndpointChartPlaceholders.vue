/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'

const {t} = useI18n()
</script>

<template>
  <div class="grid grid-cols-1 lg:grid-cols-2 gap-4 mb-4">
    <NeutralContainer>
      <SectionHeader>{{ t('apiStatus.statusCodes') }}</SectionHeader>
      <div class="h-32 flex items-center justify-center text-sm text-(--text-muted)">
        <font-awesome-icon :icon="['fas', 'chart-bar']" class="text-2xl mr-2" />
      </div>
    </NeutralContainer>
    <NeutralContainer>
      <SectionHeader>{{ t('apiStatus.responseTimeLine') }}</SectionHeader>
      <div class="h-32 flex items-center justify-center text-sm text-(--text-muted)">
        <font-awesome-icon :icon="['fas', 'chart-line']" class="text-2xl mr-2" />
      </div>
    </NeutralContainer>
  </div>
</template>
