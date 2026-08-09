/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import RegistrationsFairnessTable from './RegistrationsFairnessTable.vue'
import MutedText from '@/components/typography/MutedText.vue'

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3">
    <div class="flex items-center justify-between flex-wrap gap-2">
      <div>
        <span class="font-medium text-primary">Wettkampf Vorbereitung</span>
        <MutedText class="ml-2">
          {{ t('eventsRegistrations.deadline') }}: 20.05.2026 18:00
        </MutedText>
        <MutedText class="ml-2">
          ({{ t('eventsRegistrations.limit') }}: 12)
        </MutedText>
      </div>
      <div class="flex items-center gap-2">
        <InfoBadge>3 {{ t('eventsRegistrations.pending') }}</InfoBadge>
        <SuccessBadge>2 {{ t('eventsRegistrations.accepted') }}</SuccessBadge>
      </div>
    </div>

    <div class="border-t border-(--border) pt-3">
      <RegistrationsFairnessTable/>
    </div>
  </NeutralContainer>
</template>
