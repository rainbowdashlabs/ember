/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'

const props = defineProps<{
  currentIndex: number
  totalSections: number
  currentSectionDone: boolean
  saving: boolean
}>()

defineEmits<{
  (e: 'markDoneAndNext'): void
  (e: 'markDoneAndExit'): void
  (e: 'prev'): void
  (e: 'next'): void
  (e: 'finish'): void
}>()

const { t } = useI18n()

const isLast = () => props.currentIndex === props.totalSections - 1
const hasNext = () => props.currentIndex < props.totalSections - 1
const hasPrev = () => props.currentIndex > 0
</script>

<template>
  <div class="space-y-2">
    <div class="flex items-center gap-2">
      <SuccessButton v-if="!currentSectionDone && hasNext()" class="flex-1 sm:flex-initial" :disabled="saving" @click="$emit('markDoneAndNext')">
        <font-awesome-icon :icon="['fas', 'check']" class="mr-1" /> {{ t('protocol.markDoneAndNext') }}
      </SuccessButton>
      <SuccessButton v-if="!currentSectionDone" class="flex-1 sm:flex-initial" :disabled="saving" @click="$emit('markDoneAndExit')">
        <font-awesome-icon :icon="['fas', 'check']" class="mr-1" /> {{ t('protocol.markDoneAndExit') }}
      </SuccessButton>
    </div>
    <div class="flex items-center gap-2">
      <SecondaryButton v-if="hasPrev()" class="flex-1 sm:flex-initial" :disabled="saving" @click="$emit('prev')">
        <font-awesome-icon :icon="['fas', 'chevron-left']" class="mr-1" /> {{ t('protocol.prevSection') }}
      </SecondaryButton>
      <div class="hidden sm:block flex-1" />
      <SuccessButton v-if="isLast()" class="flex-1 sm:flex-initial" :disabled="saving" @click="$emit('finish')">
        <font-awesome-icon :icon="['fas', 'flag']" class="mr-1" /> {{ t('protocol.finish') }}
      </SuccessButton>
      <PrimaryButton v-if="hasNext()" class="flex-1 sm:flex-initial" :disabled="saving" @click="$emit('next')">
        {{ t('protocol.nextSection') }} <font-awesome-icon :icon="['fas', 'chevron-right']" class="ml-1" />
      </PrimaryButton>
    </div>
  </div>
</template>
