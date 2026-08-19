/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed, nextTick } from 'vue'

interface FieldInfo {
  name: string
  type?: string
}

const modelValue = defineModel<string>({required: true})

const props = defineProps<{
  placeholder?: string
  fields: FieldInfo[]
}>()

const inputRef = ref<HTMLInputElement | null>(null)
const showSuggestions = ref(false)
const selectedIndex = ref(0)
const cursorPos = ref(0)

const builtInFields = [
  'wartezeit_tage', 'wartezeit_monate', 'wartezeit_quartale', 'wartezeit_jahre',
]

const allSuggestions = computed(() => {
  const fieldSuggestions = props.fields.map(f => ({ label: f.name, hint: f.type ?? '', wrap: 'bracket' as const }))
  const builtIn = builtInFields.map(f => ({ label: f, hint: 'Wartezeit', wrap: 'bracket' as const }))
  const ageFunctions = props.fields.map(f => ({ label: `age([${f.name}])`, hint: 'Alter', wrap: 'none' as const }))
  return [...fieldSuggestions, ...builtIn, ...ageFunctions]
})

/** What has been typed inside the open bracket, or null when the cursor is not in one. */
const currentPartial = ref<string | null>(null)

const filteredSuggestions = computed(() => {
  const partial = currentPartial.value
  if (partial === null) return []
  const term = partial.toLowerCase()
  if (!term) return allSuggestions.value
  return allSuggestions.value.filter(s => s.label.toLowerCase().includes(term))
})

/**
 * An open bracket with nothing typed yet is the moment somebody who does not remember the field
 * names needs them, so it reads as an empty term rather than as no bracket at all.
 */
function extractPartial(text: string, pos: number): string | null {
  const bracketStart = text.lastIndexOf('[', pos - 1)
  if (bracketStart === -1) return null
  const closeBracket = text.indexOf(']', bracketStart)
  if (closeBracket !== -1 && closeBracket < pos) return null
  return text.substring(bracketStart + 1, pos)
}

function onInput(event: Event) {
  const input = event.target as HTMLInputElement
  const pos = input.selectionStart ?? 0
  cursorPos.value = pos
  currentPartial.value = extractPartial(input.value, pos)
  modelValue.value = input.value
  showSuggestions.value = currentPartial.value !== null
  selectedIndex.value = 0
}

function onKeydown(event: KeyboardEvent) {
  if (!showSuggestions.value) return
  if (event.key === 'ArrowDown') {
    event.preventDefault()
    selectedIndex.value = Math.min(selectedIndex.value + 1, filteredSuggestions.value.length - 1)
  } else if (event.key === 'ArrowUp') {
    event.preventDefault()
    selectedIndex.value = Math.max(selectedIndex.value - 1, 0)
  } else if (event.key === 'Enter' || event.key === 'Tab') {
    const suggestion = filteredSuggestions.value[selectedIndex.value]
    if (suggestion) {
      event.preventDefault()
      applySuggestion(suggestion)
    }
  } else if (event.key === 'Escape') {
    showSuggestions.value = false
  }
}

function applySuggestion(suggestion: { label: string; wrap: 'bracket' | 'none' }) {
  const text = modelValue.value
  const pos = cursorPos.value
  const bracketStart = text.lastIndexOf('[', pos - 1)
  if (bracketStart === -1) return

  let newValue: string
  let newPos: number

  if (suggestion.wrap === 'none') {
    // Replace everything from the opening [ with the full label (e.g. age([field]))
    const before = text.substring(0, bracketStart)
    const after = text.substring(pos)
    const trimmedAfter = after.startsWith(']') ? after.substring(1) : after
    newValue = before + suggestion.label + trimmedAfter
    newPos = before.length + suggestion.label.length
  } else {
    const before = text.substring(0, bracketStart + 1)
    const after = text.substring(pos)
    const needsClose = !after.startsWith(']')
    newValue = before + suggestion.label + (needsClose ? ']' : '') + after
    newPos = bracketStart + 1 + suggestion.label.length + (needsClose ? 1 : 0)
  }

  modelValue.value = newValue
  showSuggestions.value = false
  currentPartial.value = null
  nextTick(() => {
    if (inputRef.value) {
      inputRef.value.setSelectionRange(newPos, newPos)
      inputRef.value.focus()
    }
  })
}

function onClick() {
  if (inputRef.value) {
    const pos = inputRef.value.selectionStart ?? 0
    cursorPos.value = pos
    currentPartial.value = extractPartial(inputRef.value.value, pos)
    showSuggestions.value = currentPartial.value !== null
  }
}

function onBlur() {
  setTimeout(() => { showSuggestions.value = false }, 200)
}
</script>

<template>
  <div class="relative">
    <input
      ref="inputRef"
      :value="modelValue"
      :placeholder="placeholder"
      class="w-full rounded-theme border border-bg-light-accent dark:border-bg-dark-accent bg-bg-light dark:bg-bg-dark text-(--text) px-3 py-1.5 text-sm outline-none focus:border-primary focus:ring-1 focus:ring-primary transition-colors duration-150 disabled:opacity-50 disabled:cursor-not-allowed font-mono"
      type="text"
      @input="onInput"
      @keydown="onKeydown"
      @click="onClick"
      @blur="onBlur"
    />
    <div
      v-if="showSuggestions && filteredSuggestions.length > 0"
      class="absolute left-0 right-0 top-full mt-1 z-20 rounded-theme border border-bg-light-accent dark:border-bg-dark-accent bg-bg-light dark:bg-bg-dark shadow-lg max-h-48 overflow-y-auto"
    >
      <button
        v-for="(suggestion, i) in filteredSuggestions"
        :key="suggestion.label"
        :class="i === selectedIndex ? 'bg-primary/10 text-primary' : 'hover:bg-(--bg-accent)/30'"
        class="w-full text-left px-3 py-1.5 text-sm font-mono flex items-center justify-between gap-2"
        @mousedown.prevent="applySuggestion(suggestion)"
      >
        <span>{{ suggestion.wrap === 'bracket' ? `[${suggestion.label}]` : suggestion.label }}</span>
        <span v-if="suggestion.hint" class="text-xs text-(--text-muted) font-sans">{{ suggestion.hint }}</span>
      </button>
    </div>
  </div>
</template>
