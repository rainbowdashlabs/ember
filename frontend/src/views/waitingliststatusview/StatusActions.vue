/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'

const props = defineProps<{ confirming: boolean }>()
const emit = defineEmits<{
  (e: 'confirm'): void
  (e: 'remove'): void
}>()

const { t } = useI18n()
</script>

<template>
  <div class="flex flex-col sm:flex-row gap-3">
    <PrimaryButton :icon="['fas', 'check']" :disabled="props.confirming" class="flex-1" @click="emit('confirm')">
      {{ props.confirming ? t('common.loading') : t('waitingList.publicStatus.confirmInterest') }}
    </PrimaryButton>
    <ErrorButton :icon="['fas', 'xmark']" class="flex-1" @click="emit('remove')">
      {{ t('waitingList.publicStatus.removeFromList') }}
    </ErrorButton>
  </div>
</template>
