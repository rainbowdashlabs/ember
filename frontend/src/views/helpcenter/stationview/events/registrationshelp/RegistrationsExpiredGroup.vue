/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import ErrorContainer from '@/components/container/ErrorContainer.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import MutedText from '@/components/typography/MutedText.vue'

const {t} = useI18n()
</script>

<template>
  <ErrorContainer class="space-y-3">
    <div class="flex items-center justify-between flex-wrap gap-2">
      <div>
        <span class="font-medium text-primary">Erste-Hilfe-Kurs</span>
        <MutedText class="ml-2">
          {{ t('eventsRegistrations.deadline') }}: 10.05.2026 12:00
        </MutedText>
      </div>
      <div class="flex items-center gap-2">
        <InfoBadge>1 {{ t('eventsRegistrations.pending') }}</InfoBadge>
        <ErrorBadge>{{ t('eventsRegistrations.expired') }}</ErrorBadge>
      </div>
    </div>
  </ErrorContainer>
</template>
