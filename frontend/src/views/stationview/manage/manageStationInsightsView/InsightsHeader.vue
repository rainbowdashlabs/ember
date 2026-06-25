/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import PageHeader from '@/components/typography/PageHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import HelpCenterHint from '@/components/help/HelpCenterHint.vue'

const {t} = useI18n()
</script>

<template>
  <div class="flex flex-wrap items-center justify-between gap-3">
    <div>
      <PageHeader>{{ t('insights.title') }}</PageHeader>
      <MutedText tag="p" size="sm">{{ t('insights.subtitle') }}</MutedText>
    </div>
    <HelpCenterHint :to="{name: 'help-station-insights'}">
      {{ t('insights.help') }}
    </HelpCenterHint>
  </div>
</template>
