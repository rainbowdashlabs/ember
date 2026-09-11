/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import ErrorContainer from '@/components/container/ErrorContainer.vue'
import InfoContainer from '@/components/container/InfoContainer.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'

/**
 * What a list of problems amounts to, above the list itself: how many errors, how many warnings,
 * and the all clear when there is neither.
 */
defineProps<{
  errorCount: number
  warnCount: number
  /** The all clear, which reads differently depending on whose problems are being counted. */
  emptyLabel: string
}>()

const {t} = useI18n()
</script>

<template>
  <div class="flex gap-3 mb-4">
    <ErrorContainer v-if="errorCount > 0" class="flex items-center gap-2 !py-2 !px-3">
      <font-awesome-icon :icon="['fas', 'circle-xmark']"/>
      <span class="font-semibold">{{ errorCount }}</span> {{ t('adminProblems.errors') }}
    </ErrorContainer>
    <InfoContainer v-if="warnCount > 0" class="flex items-center gap-2 !py-2 !px-3">
      <font-awesome-icon :icon="['fas', 'triangle-exclamation']"/>
      <span class="font-semibold">{{ warnCount }}</span> {{ t('adminProblems.warnings') }}
    </InfoContainer>
    <NeutralContainer v-if="errorCount === 0 && warnCount === 0" class="flex items-center gap-2 !py-2 !px-3">
      <font-awesome-icon :icon="['fas', 'circle-check']" class="text-(--success)"/>
      {{ emptyLabel }}
    </NeutralContainer>
  </div>
</template>
