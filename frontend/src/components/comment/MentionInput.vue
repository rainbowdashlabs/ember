/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, nextTick, onMounted, ref, watch} from 'vue'
import type {MemberCompletion} from '@/api/stationMembers'

const props = defineProps<{
  members: MemberCompletion[]
  placeholder?: string
}>()

const model = defineModel<string>({required: true})

const editorRef = ref<HTMLDivElement | null>(null)
const showDropdown = ref(false)
const searchQuery = ref('')
const mentionStart = ref(-1)
const mentionStartNode = ref<Node | null>(null)
const selectedIndex = ref(0)
const dropdownTop = ref(0)
const dropdownLeft = ref(0)

const filteredMembers = computed(() => {
  if (!searchQuery.value) return props.members.slice(0, 8)
  const q = searchQuery.value.toLowerCase()
  return props.members
      .filter(m => m.name.toLowerCase().includes(q))
      .slice(0, 8)
})

function rawToHtml(raw: string): string {
  if (!raw) return ''
  const escaped = raw
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
  // New format: @[stationUid/memberUid:Name]
  let result = escaped.replace(/@\[([^/]+)\/([^:]+):([^\]]+)]/g, (_match, stationUid, memberUid, name) => {
    const member = props.members.find(m => m.memberUid === memberUid)
    const displayName = member?.name?.trim() || name
    return `<span contenteditable="false" data-mention-station="${stationUid}" data-mention-member="${memberUid}" data-mention-name="${name.replace(/"/g, '&quot;')}" class="mention-chip">@${displayName}</span>`
  })
  // Legacy format: @[123:Name]
  result = result.replace(/@\[(\d+):([^\]]+)]/g, (_match, id, name) => {
    const member = props.members.find(m => m.id === parseInt(id))
    const displayName = member?.name?.trim() || name
    return `<span contenteditable="false" data-mention-station="" data-mention-member="" data-mention-name="${name.replace(/"/g, '&quot;')}" class="mention-chip">@${displayName}</span>`
  })
  return result.replace(/\n/g, '<br>')
}

function htmlToRaw(el: HTMLElement): string {
  let result = ''
  for (const node of el.childNodes) {
    if (node.nodeType === Node.TEXT_NODE) {
      result += node.textContent ?? ''
    } else if (node.nodeType === Node.ELEMENT_NODE) {
      const element = node as HTMLElement
      if (element.dataset.mentionMember) {
        const stationUid = element.dataset.mentionStation
        const memberUid = element.dataset.mentionMember
        const name = element.dataset.mentionName
        result += `@[${stationUid}/${memberUid}:${name}]`
      } else if (element.tagName === 'BR') {
        result += '\n'
      } else {
        result += htmlToRaw(element)
      }
    }
  }
  return result
}

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
    editorRef.value.innerHTML = rawToHtml(model.value)
  }
}

onMounted(() => {
  if (editorRef.value && model.value) {
    editorRef.value.innerHTML = rawToHtml(model.value)
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

function updateDropdownPosition() {
  const sel = window.getSelection()
  if (!sel || sel.rangeCount === 0 || !editorRef.value) return

  const range = sel.getRangeAt(0).cloneRange()
  range.collapse(true)
  const rect = range.getBoundingClientRect()
  const editorRect = editorRef.value.getBoundingClientRect()

  if (rect.width === 0 && rect.height === 0) {
    // Fallback when getBoundingClientRect returns empty (e.g. empty line)
    dropdownTop.value = editorRect.height + 4
    dropdownLeft.value = 0
  } else {
    dropdownTop.value = rect.bottom - editorRect.top + 4
    dropdownLeft.value = Math.max(0, rect.left - editorRect.left)
  }
}

function checkForMention() {
  const sel = window.getSelection()
  if (!sel || sel.rangeCount === 0) return

  const range = sel.getRangeAt(0)
  const node = range.startContainer
  if (node.nodeType !== Node.TEXT_NODE) {
    showDropdown.value = false
    return
  }

  const text = node.textContent ?? ''
  const cursor = range.startOffset
  const beforeCursor = text.substring(0, cursor)
  const lastAt = beforeCursor.lastIndexOf('@')

  if (lastAt >= 0) {
    const afterAt = beforeCursor.substring(lastAt + 1)
    if (!afterAt.includes(' ') && (lastAt === 0 || text[lastAt - 1] === ' ' || text[lastAt - 1] === '\n')) {
      searchQuery.value = afterAt
      mentionStart.value = lastAt
      mentionStartNode.value = node
      selectedIndex.value = 0
      showDropdown.value = true
      updateDropdownPosition()
      return
    }
  }
  showDropdown.value = false
}

function selectMember(member: MemberCompletion) {
  const editor = editorRef.value
  const node = mentionStartNode.value
  if (!editor || !node || node.nodeType !== Node.TEXT_NODE) return

  const name = member.name.trim() || `#${member.id}`
  const text = node.textContent ?? ''
  const sel = window.getSelection()
  const cursor = sel?.getRangeAt(0).startOffset ?? text.length

  const before = text.substring(0, mentionStart.value)
  const after = text.substring(cursor)

  const chip = document.createElement('span')
  chip.contentEditable = 'false'
  chip.dataset.mentionStation = member.stationUid
  chip.dataset.mentionMember = member.memberUid
  chip.dataset.mentionName = name
  chip.className = 'mention-chip'
  chip.textContent = `@${name}`

  const parent = node.parentNode!
  const beforeNode = document.createTextNode(before)
  const spaceAfterNode = document.createTextNode('\u00A0' + after)

  parent.insertBefore(beforeNode, node)
  parent.insertBefore(chip, node)
  parent.insertBefore(spaceAfterNode, node)
  parent.removeChild(node)

  nextTick(() => {
    const range = document.createRange()
    range.setStart(spaceAfterNode, 1)
    range.collapse(true)
    sel?.removeAllRanges()
    sel?.addRange(range)
    editor.focus()
  })

  showDropdown.value = false
  syncToModel()
}

function onKeydown(e: KeyboardEvent) {
  if (showDropdown.value) {
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      selectedIndex.value = Math.min(selectedIndex.value + 1, filteredMembers.value.length - 1)
      return
    }
    if (e.key === 'ArrowUp') {
      e.preventDefault()
      selectedIndex.value = Math.max(selectedIndex.value - 1, 0)
      return
    }
    if (e.key === 'Enter') {
      e.preventDefault()
      const member = filteredMembers.value[selectedIndex.value]
      if (member) selectMember(member)
      return
    }
    if (e.key === 'Escape') {
      showDropdown.value = false
      e.preventDefault()
      return
    }
  }
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
        class="mention-editor w-full px-3 py-2 rounded-theme border border-bg-light-accent bg-bg-light text-[var(--text)] placeholder-[var(--text-muted)] transition-colors duration-150 outline-none focus:border-primary focus:ring-1 focus:ring-primary dark:border-bg-dark-accent dark:bg-bg-dark"
        @input="onInput"
        @keydown="onKeydown"
        @paste="onPaste"
    />
    <!-- Mention dropdown -->
    <div
        v-if="showDropdown && filteredMembers.length > 0"
        :style="{ top: dropdownTop + 'px', left: dropdownLeft + 'px' }"
        class="absolute z-30 w-64 max-h-48 overflow-y-auto rounded-theme border border-bg-light-accent bg-bg-light shadow-lg dark:border-bg-dark-accent dark:bg-bg-dark"
    >
      <button
          v-for="(m, i) in filteredMembers"
          :key="m.id"
          type="button"
          class="w-full px-3 py-2 text-left text-sm transition-colors"
          :class="i === selectedIndex ? 'bg-primary/15 text-primary' : 'hover:bg-primary/10'"
          @mousedown.prevent="selectMember(m)"
          @mouseenter="selectedIndex = i"
      >
        <span class="font-medium">{{ m.name }}</span>
      </button>
    </div>
  </div>
</template>

<style scoped>
.mention-editor {
  min-height: 4.5rem;
  max-height: 12rem;
  overflow-y: auto;
  white-space: pre-wrap;
  word-wrap: break-word;
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
</style>
