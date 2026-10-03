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
  apply: [url: string]
  cancel: []
}>()

const { t } = useI18n()
const urlId = useId()
const videoUrl = ref('')
const detectedProvider = ref('')

function onInput() {
  const url = videoUrl.value
  if (!url) { detectedProvider.value = ''; return }
  if (/youtube\.com|youtu\.be/i.test(url)) detectedProvider.value = 'YouTube'
  else if (/vimeo\.com/i.test(url)) detectedProvider.value = 'Vimeo'
  else if (url.toLowerCase().includes('/videos/watch/')) detectedProvider.value = 'PeerTube'
  else if (/dailymotion\.com|dai\.ly/i.test(url)) detectedProvider.value = 'Dailymotion'
  else if (url.startsWith('http')) detectedProvider.value = 'Video'
  else detectedProvider.value = ''
}
</script>

<template>
  <div
    class="absolute z-30 w-80 rounded-lg shadow-lg border border-[var(--border)] bg-[var(--bg)] p-3 space-y-2"
    :style="{ top: position.top + 'px', left: position.left + 'px' }"
  >
    <div class="flex items-center justify-between">
      <div class="flex items-center gap-2">
        <font-awesome-icon :icon="['fas', 'play']" class="text-[var(--color-primary)] w-3.5 h-3.5" />
        <span class="text-sm font-medium">{{ t('markdownEditor.embedVideo') }}</span>
      </div>
      <MutedIconButton :icon="['fas', 'xmark']" :label="t('common.close')" hover="text" @click="$emit('cancel')"/>
    </div>

    <div>
      <label :for="urlId" class="block text-xs text-[var(--text-muted)] mb-0.5">{{ t('markdownEditor.videoUrl') }}</label>
      <TextInput :id="urlId" v-model="videoUrl" placeholder="https://www.youtube.com/watch?v=..." class="!text-sm" @input="onInput" />
    </div>

    <p v-if="detectedProvider" class="text-xs text-[var(--text-muted)]">
      <font-awesome-icon :icon="['fas', 'check']" class="text-[var(--color-success)] mr-1" />
      {{ t('markdownEditor.providerDetected', {provider: detectedProvider}) }}
    </p>
    <p class="text-[10px] text-[var(--text-muted)]">{{ t('markdownEditor.videoProviders') }}</p>

    <ButtonRow pair>
      <PrimaryButton compact :disabled="!videoUrl" @click="$emit('apply', videoUrl)">
        <font-awesome-icon :icon="['fas', 'check']" class="mr-1" /> {{ t('common.insert') }}
      </PrimaryButton>
      <SecondaryButton compact @click="$emit('cancel')">{{ t('common.cancel') }}</SecondaryButton>
    </ButtonRow>
  </div>
</template>
