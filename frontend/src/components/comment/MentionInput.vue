/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import type {StationMember} from '@/api/types'

const props = defineProps<{
  members: StationMember[]
  placeholder?: string
}>()

const model = defineModel<string>({required: true})

const textareaRef = ref<HTMLTextAreaElement | null>(null)
const showDropdown = ref(false)
const searchQuery = ref('')
const mentionStart = ref(-1)

const filteredMembers = computed(() => {
  if (!searchQuery.value) return props.members.slice(0, 8)
  const q = searchQuery.value.toLowerCase()
  return props.members
      .filter(m => (m.name?.toLowerCase().includes(q)) || (m.email?.toLowerCase().includes(q)))
      .slice(0, 8)
})

function onInput(e: Event) {
  const textarea = e.target as HTMLTextAreaElement
  const text = textarea.value
  const cursor = textarea.selectionStart

  // Check if we're in a mention context
  const beforeCursor = text.substring(0, cursor)
  const lastAt = beforeCursor.lastIndexOf('@')

  if (lastAt >= 0) {
    const afterAt = beforeCursor.substring(lastAt + 1)
    // Only show if no space in the mention query and it's the start or preceded by space
    if (!afterAt.includes(' ') && (lastAt === 0 || text[lastAt - 1] === ' ' || text[lastAt - 1] === '\n')) {
      searchQuery.value = afterAt
      mentionStart.value = lastAt
      showDropdown.value = true
      return
    }
  }
  showDropdown.value = false
}

function selectMember(member: StationMember) {
  const text = model.value
  const name = member.name?.trim() || member.email || `#${member.id}`
  const mention = `@[${member.id}:${name}]`
  const before = text.substring(0, mentionStart.value)
  const textarea = textareaRef.value
  const cursor = textarea?.selectionStart ?? text.length
  const after = text.substring(cursor)

  model.value = before + mention + ' ' + after
  showDropdown.value = false

  // Focus back
  setTimeout(() => {
    if (textarea) {
      const pos = before.length + mention.length + 1
      textarea.focus()
      textarea.setSelectionRange(pos, pos)
    }
  }, 10)
}

function onKeydown(e: KeyboardEvent) {
  if (showDropdown.value && e.key === 'Escape') {
    showDropdown.value = false
    e.preventDefault()
  }
}
</script>

<template>
  <div class="relative">
    <textarea
        ref="textareaRef"
        :value="model"
        :placeholder="placeholder"
        :rows="3"
        class="w-full px-3 py-2 rounded-theme border border-bg-light-accent bg-bg-light text-[var(--text)] placeholder-[var(--text-muted)] transition-colors duration-150 outline-none focus:border-primary focus:ring-1 focus:ring-primary dark:border-bg-dark-accent dark:bg-bg-dark resize-y"
        @input="e => { model = (e.target as HTMLTextAreaElement).value; onInput(e) }"
        @keydown="onKeydown"
    />
    <!-- Mention dropdown -->
    <div
        v-if="showDropdown && filteredMembers.length > 0"
        class="absolute z-30 mt-1 w-64 max-h-48 overflow-y-auto rounded-theme border border-bg-light-accent bg-bg-light shadow-lg dark:border-bg-dark-accent dark:bg-bg-dark"
    >
      <button
          v-for="m in filteredMembers"
          :key="m.id"
          type="button"
          class="w-full px-3 py-2 text-left text-sm hover:bg-primary/10 transition-colors"
          @mousedown.prevent="selectMember(m)"
      >
        <span class="font-medium">{{ m.name || m.email }}</span>
        <span v-if="m.name && m.email" class="text-xs text-(--text-muted) ml-2">{{ m.email }}</span>
      </button>
    </div>
  </div>
</template>
