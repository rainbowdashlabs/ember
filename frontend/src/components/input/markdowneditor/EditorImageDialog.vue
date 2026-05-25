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

defineProps<{
  fileId?: number
  position: { top: number; left: number }
}>()

const emit = defineEmits<{
  insertUrl: [url: string, alt: string]
  uploadFile: [file: File, alt: string]
  cancel: []
}>()

const imageUrl = ref('')
const imageAlt = ref('')
const uploading = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)

async function handleUpload(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  uploading.value = true
  emit('uploadFile', file, imageAlt.value || file.name)
  input.value = ''
}
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
      <button type="button" class="text-[var(--text-muted)] hover:text-[var(--text)] cursor-pointer" @click="$emit('cancel')">
        <font-awesome-icon :icon="['fas', 'xmark']" class="w-3.5 h-3.5" />
      </button>
    </div>

    <div>
      <label class="block text-xs text-[var(--text-muted)] mb-0.5">Alternativtext</label>
      <TextInput v-model="imageAlt" placeholder="Bildbeschreibung" class="!text-sm" />
    </div>

    <div>
      <label class="block text-xs text-[var(--text-muted)] mb-0.5">Bild-URL</label>
      <TextInput v-model="imageUrl" placeholder="https://..." class="!text-sm" />
    </div>

    <input ref="fileInput" type="file" accept="image/png,image/jpeg,image/webp" class="hidden" @change="handleUpload" />

    <div class="flex items-center gap-2">
      <PrimaryButton compact v-if="imageUrl" @click="$emit('insertUrl', imageUrl, imageAlt)">
        <font-awesome-icon :icon="['fas', 'check']" class="mr-1" /> Einfügen
      </PrimaryButton>
      <SecondaryButton compact v-if="fileId" :disabled="uploading" @click="fileInput?.click()">
        <font-awesome-icon :icon="['fas', uploading ? 'spinner' : 'upload']" :spin="uploading" class="mr-1" /> Hochladen
      </SecondaryButton>
      <SecondaryButton compact @click="$emit('cancel')">Abbrechen</SecondaryButton>
    </div>
  </div>
</template>
