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
  /** Comma-separated list of accepted MIME types or file extensions (passed to <input accept>). */
  accept?: string
  /** Maximum file size in bytes. Files exceeding this are rejected with an inline error. */
  maxSize: number
  disabled?: boolean
  /** External error message (e.g. from a failed upload request); takes precedence over the local size error. */
  error?: string | null
  /** Optional override for the button label. Defaults to "Datei wählen". */
  label?: string
  /** Optional hint shown beneath the row (e.g. file format guidance). The size limit is always appended. */
  hint?: string
}>(), {
  disabled: false,
  error: null,
})

const emit = defineEmits<{
  /** Fired only when the picked file is within the size budget. */
  select: [file: File]
  /** Fired when a file is too large; parents that want to do their own error handling can listen. */
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

// Clear the local size error when the parent's error message changes (assume a fresh attempt).
watch(() => props.error, () => { if (props.error) sizeError.value = null })
</script>

<template>
  <div class="space-y-1">
    <div class="flex flex-wrap items-center gap-2">
      <FileUploadButton :accept="accept" :disabled="disabled" @select="onSelect">
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
