/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'

defineProps<{
  canManage: boolean
  isMobile: boolean
}>()

const emit = defineEmits<{
  (e: 'back'): void
  (e: 'manage-fields'): void
  (e: 'delete-list'): void
}>()

const { t } = useI18n()
</script>

<template>
  <div class="flex flex-wrap items-center justify-between gap-2">
    <SecondaryButton :icon="['fas', 'chevron-left']" @click="emit('back')">
      {{ t('waitingList.back') }}
    </SecondaryButton>
    <div v-if="canManage" class="flex items-center gap-2 w-full sm:w-auto">
      <SecondaryButton :icon="['fas', 'sliders']" :full-width="isMobile" class="flex-1 sm:flex-initial" @click="emit('manage-fields')">
        {{ t('waitingList.manageFields') }}
      </SecondaryButton>
      <ErrorButton :icon="['fas', 'trash']" :full-width="isMobile" class="flex-1 sm:flex-initial" @click="emit('delete-list')">
        {{ t('waitingList.deleteList') }}
      </ErrorButton>
    </div>
  </div>
</template>
