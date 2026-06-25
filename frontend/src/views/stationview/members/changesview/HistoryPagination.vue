/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'

const {t} = useI18n()

defineProps<{
  offset: number
  limit: number
  total: number
  page: number
  totalPages: number
}>()

const emit = defineEmits<{
  prev: []
  next: []
}>()
</script>

<template>
  <div v-if="total > limit" class="flex items-center justify-between pt-2">
    <SecondaryButton :icon="['fas', 'chevron-left']" :disabled="offset === 0" @click="emit('prev')">
      {{ t('common.back') }}
    </SecondaryButton>
    <span class="text-sm text-(--text-muted)">
      {{ t('memberChanges.page', { current: page, total: totalPages }) }}
    </span>
    <SecondaryButton :disabled="offset + limit >= total" @click="emit('next')">
      {{ t('memberChanges.next') }}
      <font-awesome-icon :icon="['fas', 'chevron-right']" class="ml-1"/>
    </SecondaryButton>
  </div>
</template>
