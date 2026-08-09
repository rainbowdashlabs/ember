/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {onMounted, ref, watch} from 'vue'
import type {MemberCompletion} from '@/api/stationMembers'
import type {MemberGroup} from '@/api/types'
import {useMentionQuery, type SpecialMention as MentionSpecialMention, type Suggestion as MentionSuggestion} from '@/composables/useMentionQuery'
import {htmlToRaw, rawToHtml} from './mentioninput/mentionMarkup'
import MentionSuggestionList from './mentioninput/MentionSuggestionList.vue'

export type SpecialMention = MentionSpecialMention
export type Suggestion = MentionSuggestion

const props = withDefaults(defineProps<{
  members: MemberCompletion[]
  groups?: MemberGroup[]
  specialMentions?: SpecialMention[]
  placeholder?: string
  expanded?: boolean
}>(), {
  groups: () => [],
  specialMentions: () => [],
  expanded: true,
})

const model = defineModel<string>({required: true})

const editorRef = ref<HTMLDivElement | null>(null)

function syncToModel() {
  if (!editorRef.value) return
  const raw = htmlToRaw(editorRef.value)
  if (raw !== model.value) {
    model.value = raw
  }
}

function syncFromModel() {
  if (!editorRef.value) return
  const currentRaw = htmlToRaw(editorRef.value)
  if (currentRaw !== model.value) {
    editorRef.value.innerHTML = rawToHtml(model.value, props.members)
  }
}

const {
  showDropdown,
  filteredSuggestions,
  selectedIndex,
  dropdownTop,
  dropdownLeft,
  checkForMention,
  selectSuggestion,
  handleKeydown,
} = useMentionQuery(editorRef, props, syncToModel)

onMounted(() => {
  if (editorRef.value && model.value) {
    editorRef.value.innerHTML = rawToHtml(model.value, props.members)
  }
})

watch(model, () => {
  if (document.activeElement !== editorRef.value) {
    syncFromModel()
  }
})

function onInput() {
  syncToModel()
  checkForMention()
}

function onPaste(e: ClipboardEvent) {
  e.preventDefault()
  const text = e.clipboardData?.getData('text/plain') ?? ''
  document.execCommand('insertText', false, text)
}
</script>

<template>
  <div class="relative">
    <div
        ref="editorRef"
        contenteditable="true"
        :data-placeholder="placeholder"
        :class="['mention-editor w-full px-3 py-2 rounded-theme border border-bg-light-accent bg-bg-light text-[var(--text)] placeholder-[var(--text-muted)] transition-colors duration-150 outline-none focus:border-primary focus:ring-1 focus:ring-primary dark:border-bg-dark-accent dark:bg-bg-dark', expanded ? 'mention-editor--expanded' : 'mention-editor--collapsed']"
        @input="onInput"
        @keydown="handleKeydown"
        @paste="onPaste"
    />
    <MentionSuggestionList
        v-if="showDropdown && filteredSuggestions.length > 0"
        :suggestions="filteredSuggestions"
        :selected-index="selectedIndex"
        :top="dropdownTop"
        :left="dropdownLeft"
        @select="selectSuggestion"
        @hover="selectedIndex = $event"
    />
  </div>
</template>

<style scoped>
.mention-editor {
  overflow-y: auto;
  white-space: pre-wrap;
  word-wrap: break-word;
}

.mention-editor--expanded {
  min-height: 4.5rem;
  max-height: 12rem;
}

.mention-editor--collapsed {
  min-height: 2.25rem;
  max-height: 2.25rem;
  white-space: nowrap;
  overflow-x: hidden;
}

.mention-editor:empty::before {
  content: attr(data-placeholder);
  color: var(--text-muted);
  pointer-events: none;
}

.mention-editor :deep(.mention-chip) {
  display: inline;
  color: var(--primary);
  font-weight: 600;
  cursor: default;
  user-select: all;
}

.mention-editor :deep(.bulk-mention) {
  color: var(--secondary);
}
</style>
