/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import IconButton from '@/components/button/IconButton.vue'
import DummyLeftColumn from './DummyLeftColumn.vue'
import DummyRightColumn from './DummyRightColumn.vue'

const {t} = useI18n()
</script>

<template>
  <NeutralContainer>
    <div class="flex items-center gap-3 mb-4">
      <IconButton :icon="['fas', 'chevron-left']" label="Back" />
      <SectionHeader>{{ t('boards.createTicket') }}</SectionHeader>
      <span class="text-xs font-mono text-(--text-muted) bg-(--bg-accent) px-1.5 py-0.5 rounded">JF</span>
    </div>

    <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
      <DummyLeftColumn />
      <DummyRightColumn />
    </div>
  </NeutralContainer>
</template>
