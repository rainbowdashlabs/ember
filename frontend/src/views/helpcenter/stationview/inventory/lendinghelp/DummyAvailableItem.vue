/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import StationBadge from '@/components/badge/StationBadge.vue'

defineProps<{
  name: string
  station: string
  count: number
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer>
    <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
      <div class="flex flex-col gap-1">
        <div class="flex items-center gap-2 flex-wrap">
          <span class="font-medium">{{ name }}</span>
          <StationBadge :station-name="station"/>
        </div>
        <span class="text-xs text-(--text-muted)">{{ count }} {{ t('helpCenter.inventoryLending.dummyAvailableCount') }}</span>
      </div>
      <PrimaryButton :icon="['fas', 'paper-plane']">
        {{ t('helpCenter.inventoryLending.dummyRequestItem') }}
      </PrimaryButton>
    </div>
  </NeutralContainer>
</template>
