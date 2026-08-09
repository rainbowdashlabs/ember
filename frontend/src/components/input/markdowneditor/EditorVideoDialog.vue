/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref } from 'vue'
import TextInput from '@/components/input/text/TextInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import MutedIconButton from '@/components/button/MutedIconButton.vue'

defineProps<{
  position: { top: number; left: number }
}>()

const emit = defineEmits<{
  apply: [url: string]
  cancel: []
}>()

const videoUrl = ref('')
const detectedProvider = ref('')

function onInput() {
  const url = videoUrl.value
  if (!url) { detectedProvider.value = ''; return }
  if (/youtube\.com|youtu\.be/i.test(url)) detectedProvider.value = 'YouTube'
  else if (/vimeo\.com/i.test(url)) detectedProvider.value = 'Vimeo'
  else if (/\/videos\/watch\//i.test(url)) detectedProvider.value = 'PeerTube'
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
        <font-awesome-icon :icon="['fas', 'play']" class="text-[var(--primary)] w-3.5 h-3.5" />
        <span class="text-sm font-medium">Video einbetten</span>
      </div>
      <MutedIconButton :icon="['fas', 'xmark']" label="Schließen" hover="text" @click="$emit('cancel')"/>
    </div>

    <div>
      <label class="block text-xs text-[var(--text-muted)] mb-0.5">Video-URL</label>
      <TextInput v-model="videoUrl" placeholder="https://www.youtube.com/watch?v=..." class="!text-sm" @input="onInput" />
    </div>

    <p v-if="detectedProvider" class="text-xs text-[var(--text-muted)]">
      <font-awesome-icon :icon="['fas', 'check']" class="text-[var(--success)] mr-1" />
      {{ detectedProvider }} erkannt
    </p>
    <p class="text-[10px] text-[var(--text-muted)]">YouTube, Vimeo, PeerTube, Dailymotion oder beliebige Embed-URL</p>

    <div class="flex items-center gap-2">
      <PrimaryButton compact :disabled="!videoUrl" @click="$emit('apply', videoUrl)">
        <font-awesome-icon :icon="['fas', 'check']" class="mr-1" /> Einfügen
      </PrimaryButton>
      <SecondaryButton compact @click="$emit('cancel')">Abbrechen</SecondaryButton>
    </div>
  </div>
</template>
