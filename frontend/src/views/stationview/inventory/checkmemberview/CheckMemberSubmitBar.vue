/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'

defineProps<{
  /** Whether anything at all has been marked, which is what a check needs to be worth recording. */
  anyMarked: boolean
  /** Whether every piece has been marked, which decides how the button reads. */
  allMarked: boolean
  submitting: boolean
}>()

defineEmits<{
  submit: []
  cancel: []
}>()

const { t } = useI18n()
</script>

<template>
  <div class="sticky bottom-0 bg-bg-light dark:bg-bg-dark py-4 -mx-4 px-4 sm:mx-0 sm:px-0 sm:relative border-t border-bg-light-accent/50 dark:border-bg-dark-accent/50 sm:border-0 flex justify-end gap-3">
    <SecondaryButton @click="$emit('cancel')">{{ t('inventory.check.cancel') }}</SecondaryButton>
    <PrimaryButton :disabled="!anyMarked || submitting" @click="$emit('submit')">
      {{ submitting
        ? t('inventory.check.submitting')
        : allMarked ? t('inventory.check.complete') : t('inventory.check.completePartial') }}
    </PrimaryButton>
  </div>
</template>
