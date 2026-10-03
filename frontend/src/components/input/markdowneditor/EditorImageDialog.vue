/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, useId } from 'vue'
import { useI18n } from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import MutedIconButton from '@/components/button/MutedIconButton.vue'

defineProps<{
  position: { top: number; left: number }
}>()

defineEmits<{
  insertUrl: [url: string, alt: string]
  browse: [alt: string]
  cancel: []
}>()

const { t } = useI18n()
const imageUrl = ref('')
const imageAlt = ref('')
const altId = useId()
const urlId = useId()
</script>

<template>
  <div
    class="absolute z-30 w-80 rounded-lg shadow-lg border border-[var(--border)] bg-[var(--bg)] p-3 space-y-2"
    :style="{ top: position.top + 'px', left: position.left + 'px' }"
  >
    <div class="flex items-center justify-between">
      <div class="flex items-center gap-2">
        <font-awesome-icon :icon="['fas', 'image']" class="text-[var(--color-primary)] w-3.5 h-3.5" />
        <span class="text-sm font-medium">{{ t('markdownEditor.insertImage') }}</span>
      </div>
      <MutedIconButton :icon="['fas', 'xmark']" :label="t('common.close')" hover="text" @click="$emit('cancel')"/>
    </div>

    <div>
      <label :for="altId" class="block text-xs text-[var(--text-muted)] mb-0.5">{{ t('markdownEditor.altText') }}</label>
      <TextInput :id="altId" v-model="imageAlt" :placeholder="t('markdownEditor.altTextPlaceholder')" class="!text-sm" />
    </div>

    <div>
      <label :for="urlId" class="block text-xs text-[var(--text-muted)] mb-0.5">{{ t('markdownEditor.imageUrl') }}</label>
      <TextInput :id="urlId" v-model="imageUrl" placeholder="https://..." class="!text-sm" />
    </div>

    <ButtonRow>
      <PrimaryButton v-if="imageUrl" compact @click="$emit('insertUrl', imageUrl, imageAlt)">
        <font-awesome-icon :icon="['fas', 'check']" class="mr-1" /> {{ t('common.insert') }}
      </PrimaryButton>
      <SecondaryButton compact @click="$emit('browse', imageAlt)">
        <font-awesome-icon :icon="['fas', 'folder-open']" class="mr-1" /> {{ t('markdownEditor.media') }}
      </SecondaryButton>
      <SecondaryButton compact @click="$emit('cancel')">{{ t('common.cancel') }}</SecondaryButton>
    </ButtonRow>
  </div>
</template>
