/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import ErrorButton from '@/components/button/ErrorButton.vue'

const { t } = useI18n()

defineProps<{
  imagePreview: string | null
}>()

const emit = defineEmits<{
  selectImage: [event: Event]
  removeImage: []
}>()
</script>

<template>
  <div class="space-y-2">
    <label class="text-xs text-(--text-muted) block">{{ t('quiz.questions.image') }}</label>
    <div v-if="imagePreview" class="flex items-start gap-3">
      <img :src="imagePreview" alt="" class="h-24 w-24 object-cover rounded border border-bg-light-accent dark:border-bg-dark-accent" />
      <ErrorButton @click="emit('removeImage')">
        <font-awesome-icon :icon="['fas', 'trash']" class="mr-1" />
        {{ t('common.delete') }}
      </ErrorButton>
    </div>
    <label v-else class="inline-flex items-center gap-2 px-3 py-1.5 text-sm rounded-lg font-medium cursor-pointer bg-bg-light-accent dark:bg-bg-dark-accent hover:brightness-110 transition-all">
      <font-awesome-icon :icon="['fas', 'upload']" />
      {{ t('quiz.questions.uploadImage') }}
      <input type="file" accept="image/png,image/jpeg,image/webp" class="hidden" @change="(e: Event) => emit('selectImage', e)" />
    </label>
  </div>
</template>
