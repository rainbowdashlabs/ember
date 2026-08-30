/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import MutedText from '@/components/typography/MutedText.vue'

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3">
    <div class="flex items-center justify-between gap-2">
      <SubHeader>Jacken</SubHeader>
      <MutedText size="sm" class="shrink-0">1 / 1</MutedText>
    </div>

    <div class="rounded border border-bg-light-accent/50 dark:border-bg-dark-accent/50 p-3 space-y-2 ring-2 ring-error bg-error/10">
      <div class="flex flex-col sm:flex-row sm:items-center gap-2">
        <div class="flex-1 min-w-0">
          <div class="font-medium text-sm">Jacke <span class="font-normal text-(--text-muted)">[L]</span></div>
          <div class="text-xs text-(--text-muted)">INV-0015</div>
        </div>
        <div class="flex gap-1 shrink-0">
          <SuccessButton class="text-xs px-2 py-1 opacity-40">
            <font-awesome-icon :icon="['fas', 'check']" />
          </SuccessButton>
          <ErrorButton class="text-xs px-2 py-1">
            <font-awesome-icon :icon="['fas', 'xmark']" />
          </ErrorButton>
          <SecondaryButton class="text-xs px-2 py-1">
            <font-awesome-icon :icon="['fas', 'right-from-bracket']" />
          </SecondaryButton>
          <SecondaryButton :icon="['fas', 'pen']" class="text-xs px-2 py-1">
            {{ t('inventory.check.correct.action') }}
          </SecondaryButton>
        </div>
      </div>
    </div>
  </NeutralContainer>
</template>
