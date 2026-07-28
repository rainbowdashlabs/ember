/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import MutedIconButton from '@/components/button/MutedIconButton.vue'

defineProps<{
  step: number
  total: number
}>()

const emit = defineEmits<{
  (e: 'next'): void
  (e: 'prev'): void
  (e: 'skip'): void
}>()

const {t} = useI18n()
</script>

<template>
  <div class="flex items-center gap-2 shrink-0">
    <SecondaryButton :icon="['fas', 'chevron-left']" v-if="step > 0" class="text-xs" @click="emit('prev')">
      {{ t('tour.back') }}
    </SecondaryButton>
    <PrimaryButton @click="emit('next')">
      {{ step === total - 1 ? t('tour.finish') : t('tour.next') }}
      <font-awesome-icon v-if="step < total - 1" :icon="['fas', 'chevron-right']" class="ml-1"/>
    </PrimaryButton>
    <MutedIconButton
        :icon="['fas', 'xmark']"
        :label="t('tour.skip')"
        hover="text"
        @click="emit('skip')"
    />
  </div>
</template>
