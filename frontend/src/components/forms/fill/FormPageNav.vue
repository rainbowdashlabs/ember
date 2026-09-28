/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'

/**
 * The buttons at the foot of a page being filled in: back to the page the reader came from, on to
 * the next page, and on the page the form is sent from, the button that sends it.
 *
 * <p>Whatever else belongs on the row, such as leaving the form, goes in the slot at its start.
 */
defineProps<{
  canGoBack: boolean
  isLast: boolean
  /** What the button that sends the form says. */
  sendLabel: string
  sending?: boolean
  /** Whether sending is not possible yet, such as while the consent is not ticked. */
  sendDisabled?: boolean
}>()

const emit = defineEmits<{
  back: []
  next: []
  send: []
}>()

const {t} = useI18n()
</script>

<template>
  <ButtonRow align="end">
    <slot/>
    <SecondaryButton v-if="canGoBack" :icon="['fas', 'chevron-left']" data-testid="form-page-back" @click="emit('back')">
      {{ t('forms.fill.back') }}
    </SecondaryButton>
    <PrimaryButton v-if="isLast" :disabled="sending || sendDisabled" data-testid="form-send" @click="emit('send')">
      {{ sendLabel }}
    </PrimaryButton>
    <PrimaryButton v-else :icon="['fas', 'chevron-right']" data-testid="form-page-next" @click="emit('next')">
      {{ t('forms.fill.next') }}
    </PrimaryButton>
  </ButtonRow>
</template>
