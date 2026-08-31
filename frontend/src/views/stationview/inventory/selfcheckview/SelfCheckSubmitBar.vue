/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import MutedText from '@/components/typography/MutedText.vue'

/** Putting the answers away, and handing them in once nothing is left open. */
defineProps<{
  openCount: number
  saving: boolean
  submitting: boolean
}>()

const emit = defineEmits<{
  save: []
  submit: []
}>()

const {t} = useI18n()
</script>

<template>
  <div class="flex flex-wrap items-center justify-end gap-2">
    <MutedText v-if="openCount > 0" size="sm" class="mr-auto">
      {{ t('selfCheck.stillOpen', {count: openCount}) }}
    </MutedText>
    <SecondaryButton :disabled="saving" data-testid="self-check-save" @click="emit('save')">
      {{ t('selfCheck.save') }}
    </SecondaryButton>
    <PrimaryButton :disabled="submitting || openCount > 0" data-testid="self-check-submit" @click="emit('submit')">
      {{ t('selfCheck.submit') }}
    </PrimaryButton>
  </div>
</template>
