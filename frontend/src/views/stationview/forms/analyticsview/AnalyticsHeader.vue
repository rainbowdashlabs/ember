/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'

defineProps<{
  title: string
  totalResponses: number
}>()

const emit = defineEmits<{
  export: []
  back: []
}>()

const { t } = useI18n()
</script>

<template>
  <div class="flex items-center justify-between">
    <div>
      <SubHeader>{{ title }}</SubHeader>
      <p class="text-(--text-muted) text-sm">
        {{ t('forms.analytics.totalResponses') }}: {{ totalResponses }}
      </p>
    </div>
    <div class="flex gap-2">
      <SecondaryButton :icon="['fas', 'file-export']" @click="emit('export')">
        {{ t('forms.analytics.export') }}
      </SecondaryButton>
      <SecondaryButton @click="emit('back')">{{ t('common.back') }}</SecondaryButton>
    </div>
  </div>
</template>
