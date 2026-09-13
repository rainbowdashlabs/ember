/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'

/**
 * The way forward and the way back, which is the same pair on every question.
 *
 * <p>On the first question there is nothing to go back to, so that button cancels instead: a wizard
 * that can only be left through a corner cross is one people leave by reloading the page.
 */
const props = defineProps<{
  /** Which question this is, counted from one, so the first can offer cancelling instead of going back. */
  position: number
  /** Whether the chain is on screen, which is where starting replaces going on. */
  last: boolean
  canGoOn: boolean
  busy: boolean
  /** Whether a chain was resolved at all, without which there is nothing to start. */
  hasPreview: boolean
}>()

const emit = defineEmits<{
  back: []
  cancel: []
  next: []
  start: []
}>()

const {t} = useI18n()
</script>

<template>
  <div class="flex justify-end gap-3">
    <SecondaryButton v-if="props.position > 1" data-testid="wizard-back" @click="emit('back')">
      {{ t('movements.wizard.back') }}
    </SecondaryButton>
    <SecondaryButton v-else @click="emit('cancel')">{{ t('common.cancel') }}</SecondaryButton>
    <PrimaryButton v-if="!props.last" :disabled="!props.canGoOn" data-testid="wizard-next" @click="emit('next')">
      {{ t('movements.wizard.next') }}
    </PrimaryButton>
    <PrimaryButton
        v-else
        :disabled="props.busy || !props.hasPreview"
        data-testid="wizard-start"
        @click="emit('start')"
    >
      {{ t('movements.wizard.preview.start') }}
    </PrimaryButton>
  </div>
</template>
