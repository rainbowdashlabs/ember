/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import MutedIconButton from '@/components/button/MutedIconButton.vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

defineProps<{
  name: string
  size: string
  inventoryId: string
  faded?: boolean
  errorBorder?: boolean
  showExchangeIcon?: boolean
  exchangeLabel?: string
}>()
</script>

<template>
  <NeutralContainer :class="[faded ? 'opacity-60' : '', errorBorder ? 'border-error' : '']">
    <div class="flex items-start justify-between gap-2">
      <div>
        <div class="font-medium text-sm">{{ name }} <span class="font-normal text-(--text-muted)">[{{ size }}]</span></div>
        <div class="text-xs text-(--text-muted)">{{ inventoryId }}</div>
        <slot/>
      </div>
      <MutedIconButton
        v-if="showExchangeIcon"
        :icon="['fas', 'rotate']"
        :label="exchangeLabel ?? ''"
      />
    </div>
  </NeutralContainer>
</template>
