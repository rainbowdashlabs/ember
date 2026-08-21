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

defineEmits<{
  insertUrl: [url: string, alt: string]
  browse: [alt: string]
  cancel: []
}>()

const imageUrl = ref('')
const imageAlt = ref('')
</script>

<template>
  <div
    class="absolute z-30 w-80 rounded-lg shadow-lg border border-[var(--border)] bg-[var(--bg)] p-3 space-y-2"
    :style="{ top: position.top + 'px', left: position.left + 'px' }"
  >
    <div class="flex items-center justify-between">
      <div class="flex items-center gap-2">
        <font-awesome-icon :icon="['fas', 'image']" class="text-[var(--primary)] w-3.5 h-3.5" />
        <span class="text-sm font-medium">Bild einfügen</span>
      </div>
      <MutedIconButton :icon="['fas', 'xmark']" label="Schließen" hover="text" @click="$emit('cancel')"/>
    </div>

    <div>
      <label class="block text-xs text-[var(--text-muted)] mb-0.5">Alternativtext</label>
      <TextInput v-model="imageAlt" placeholder="Bildbeschreibung" class="!text-sm" />
    </div>

    <div>
      <label class="block text-xs text-[var(--text-muted)] mb-0.5">Bild-URL</label>
      <TextInput v-model="imageUrl" placeholder="https://..." class="!text-sm" />
    </div>

    <div class="flex items-center gap-2">
      <PrimaryButton compact v-if="imageUrl" @click="$emit('insertUrl', imageUrl, imageAlt)">
        <font-awesome-icon :icon="['fas', 'check']" class="mr-1" /> Einfügen
      </PrimaryButton>
      <SecondaryButton compact @click="$emit('browse', imageAlt)">
        <font-awesome-icon :icon="['fas', 'folder-open']" class="mr-1" /> Medien
      </SecondaryButton>
      <SecondaryButton compact @click="$emit('cancel')">Abbrechen</SecondaryButton>
    </div>
  </div>
</template>
