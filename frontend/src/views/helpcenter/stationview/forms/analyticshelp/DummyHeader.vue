/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'

const {t} = useI18n()
</script>

<template>
  <div class="flex items-center justify-between mb-4">
    <div>
      <SectionHeader class="text-xl font-semibold">Zufriedenheitsumfrage</SectionHeader>
      <p class="text-(--text-muted) text-sm">{{ t('forms.analytics.totalResponses') }}: 12</p>
    </div>
    <div class="flex gap-2">
      <SecondaryButton :icon="['fas', 'file-export']" disabled>
        {{ t('forms.analytics.export') }}
      </SecondaryButton>
      <SecondaryButton disabled>{{ t('common.back') }}</SecondaryButton>
    </div>
  </div>
</template>
