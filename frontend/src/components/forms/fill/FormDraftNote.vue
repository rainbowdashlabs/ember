/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import InfoContainer from '@/components/container/InfoContainer.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import {formatDateTime} from '@/util/format'

/**
 * Says that a form continues where the reader stopped, and offers to start over instead.
 */
defineProps<{
  /** When the half-filled form was last kept, as an ISO timestamp. */
  savedAt: string
}>()

const emit = defineEmits<{
  startOver: []
}>()

const {t} = useI18n()
</script>

<template>
  <InfoContainer data-testid="form-draft-note">
    <div class="flex flex-wrap items-center justify-between gap-2">
      <p class="text-sm">{{ t('forms.draft.resumed', {when: formatDateTime(savedAt)}) }}</p>
      <SecondaryButton compact :icon="['fas', 'rotate-left']" data-testid="form-draft-start-over" @click="emit('startOver')">
        {{ t('forms.draft.startOver') }}
      </SecondaryButton>
    </div>
  </InfoContainer>
</template>
