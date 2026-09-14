/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'

defineProps<{
  canManage: boolean
}>()

const emit = defineEmits<{
  (e: 'back'): void
  (e: 'manage-fields'): void
  (e: 'delete-list'): void
}>()

const { t } = useI18n()
</script>

<template>
  <div class="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
    <ButtonRow>
      <SecondaryButton :icon="['fas', 'chevron-left']" @click="emit('back')">
        {{ t('waitingList.back') }}
      </SecondaryButton>
    </ButtonRow>
    <ButtonRow v-if="canManage" align="end">
      <SecondaryButton :icon="['fas', 'sliders']" @click="emit('manage-fields')">
        {{ t('waitingList.manageFields') }}
      </SecondaryButton>
      <ErrorButton :icon="['fas', 'trash']" @click="emit('delete-list')">
        {{ t('waitingList.deleteList') }}
      </ErrorButton>
    </ButtonRow>
  </div>
</template>
