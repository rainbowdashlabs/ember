/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'

const {t} = useI18n()
</script>

<template>
  <NeutralContainer>
    <div class="flex items-center justify-center gap-2 flex-wrap">
      <InfoBadge>{{ t('quiz.statusDraft') }}</InfoBadge>
      <font-awesome-icon :icon="['fas', 'arrow-right']" class="text-(--text-muted)" />
      <SuccessBadge>{{ t('quiz.statusActive') }}</SuccessBadge>
      <font-awesome-icon :icon="['fas', 'arrow-right']" class="text-(--text-muted)" />
      <ErrorBadge>{{ t('quiz.statusClosed') }}</ErrorBadge>
    </div>
  </NeutralContainer>
</template>
