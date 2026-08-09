/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import FileUploadButton from '@/components/button/FileUploadButton.vue'

const props = withDefaults(defineProps<{
  accept?: string
  maxSize: number
  disabled?: boolean
  error?: string | null
  label?: string
  hint?: string
  multiple?: boolean
}>(), {
  disabled: false,
  error: null,
  multiple: false,
})

const emit = defineEmits<{
  select: [file: File]
  selectMany: [files: File[]]
  tooLarge: [file: File]
}>()

const { t } = useI18n()

const sizeError = ref<string | null>(null)
const lastFileName = ref<string | null>(null)

const displayedError = computed(() => props.error || sizeError.value)

function formatBytes(bytes: number): string {
  if (bytes >= 1024 * 1024) {
    const mb = bytes / (1024 * 1024)
    return `${mb % 1 === 0 ? mb.toFixed(0) : mb.toFixed(1)} MB`
  }
  if (bytes >= 1024) {
    return `${Math.round(bytes / 1024)} KB`
  }
  return `${bytes} B`
}

const limitText = computed(() => t('fileUpload.maxSize', { size: formatBytes(props.maxSize) }))
const hintText = computed(() => props.hint ? `${props.hint} · ${limitText.value}` : limitText.value)

function onSelect(file: File) {
  sizeError.value = null
  lastFileName.value = file.name
  if (file.size > props.maxSize) {
    sizeError.value = t('fileUpload.tooLarge', { size: formatBytes(props.maxSize) })
    emit('tooLarge', file)
    return
  }
  emit('select', file)
}

function onSelectMany(files: File[]) {
  sizeError.value = null
  const accepted: File[] = []
  for (const f of files) {
    if (f.size > props.maxSize) {
      sizeError.value = t('fileUpload.tooLarge', { size: formatBytes(props.maxSize) })
      emit('tooLarge', f)
      continue
    }
    accepted.push(f)
  }
  const [firstAccepted] = accepted
  if (firstAccepted) {
    lastFileName.value = accepted.length === 1 ? firstAccepted.name : `${accepted.length} Dateien`
    emit('selectMany', accepted)
  }
}

// Clear the local size error when the parent's error message changes (assume a fresh attempt).
watch(() => props.error, () => { if (props.error) sizeError.value = null })
</script>

<template>
  <div class="space-y-1">
    <div class="flex flex-wrap items-center gap-2">
      <FileUploadButton :accept="accept" :disabled="disabled" :multiple="multiple" @select="onSelect" @select-many="onSelectMany">
        <slot>{{ label || t('fileUpload.choose') }}</slot>
      </FileUploadButton>
      <span v-if="lastFileName && !displayedError" class="text-xs text-(--text-muted) truncate max-w-xs">{{ lastFileName }}</span>
    </div>
    <p v-if="displayedError" class="text-xs text-error">
      <font-awesome-icon :icon="['fas', 'triangle-exclamation']" class="mr-1"/>{{ displayedError }}
    </p>
    <p v-else class="text-xs text-(--text-muted)">{{ hintText }}</p>
  </div>
</template>
