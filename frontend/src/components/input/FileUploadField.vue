/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import FileUploadButton from '@/components/button/FileUploadButton.vue'
import MutedText from '@/components/typography/MutedText.vue'

const props = withDefaults(defineProps<{
  accept?: string
  maxSize: number
  disabled?: boolean
  error?: string | null
  label?: string
  hint?: string
  multiple?: boolean
  /** Whether a file may also be dragged onto the field, which then shows as a drop area. */
  droppable?: boolean
}>(), {
  accept: undefined,
  disabled: false,
  error: null,
  label: undefined,
  hint: undefined,
  multiple: false,
  droppable: false,
})

const emit = defineEmits<{
  select: [file: File]
  selectMany: [files: File[]]
  tooLarge: [file: File]
}>()

const { t } = useI18n()

/** The size refusal said here, cleared once the parent reports an error of its own, which means a fresh attempt. */
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

watch(() => props.error, () => { if (props.error) sizeError.value = null })

/** Whether a file is being dragged over the drop area, which is what lights it up. */
const dragging = ref(false)

function onDragOver(event: DragEvent) {
  event.preventDefault()
  dragging.value = !props.disabled
}

/**
 * A dropped file takes the same way as a picked one, size check included. The picker's `accept`
 * does not reach a drop, so whatever receives the file still has to refuse the wrong kind.
 */
function onDrop(event: DragEvent) {
  event.preventDefault()
  dragging.value = false
  if (props.disabled) return
  const files = Array.from(event.dataTransfer?.files ?? [])
  const [first] = files
  if (props.multiple) onSelectMany(files)
  else if (first) onSelect(first)
}

const dropHandlers = computed(() => props.droppable
    ? {dragover: onDragOver, dragleave: () => { dragging.value = false }, drop: onDrop}
    : {})
</script>

<template>
  <div
      :class="{
        'rounded-theme border-2 border-dashed p-6 text-center transition-colors': droppable,
        'border-(--border)': droppable && !dragging,
        'border-primary bg-primary/10': droppable && dragging,
      }"
      class="space-y-1"
      v-on="dropHandlers"
  >
    <MutedText v-if="droppable" tag="p" size="sm">{{ t('fileUpload.dropHint') }}</MutedText>
    <div :class="{'justify-center': droppable}" class="flex flex-wrap items-center gap-2">
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
