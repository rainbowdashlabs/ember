/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, nextTick, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import type {MemberCompletion} from '@/api/stationMembers'
import type {MemberGroup} from '@/api/types'
import UserAvatar from '@/components/avatar/UserAvatar.vue'

export interface SpecialMention {
  type: string
  entityId: number
  label: string
  icon: string[]
}

export type Suggestion =
    | { kind: 'member'; data: MemberCompletion }
    | { kind: 'group'; data: MemberGroup }
    | { kind: 'special'; data: SpecialMention }

const props = withDefaults(defineProps<{
  members: MemberCompletion[]
  groups?: MemberGroup[]
  specialMentions?: SpecialMention[]
  placeholder?: string
}>(), {
  groups: () => [],
  specialMentions: () => [],
})

const {t} = useI18n()
const model = defineModel<string>({required: true})

const editorRef = ref<HTMLDivElement | null>(null)
const showDropdown = ref(false)
const searchQuery = ref('')
const mentionStart = ref(-1)
const mentionStartNode = ref<Node | null>(null)
const selectedIndex = ref(0)
const dropdownTop = ref(0)
const dropdownLeft = ref(0)

const filteredSuggestions = computed<Suggestion[]>(() => {
  const q = searchQuery.value.toLowerCase()
  const results: Suggestion[] = []

  for (const s of props.specialMentions) {
    if (!q || s.label.toLowerCase().includes(q)) {
      results.push({kind: 'special', data: s})
    }
  }
  for (const g of props.groups) {
    if (g.name && (!q || g.name.toLowerCase().includes(q))) {
      results.push({kind: 'group', data: g})
    }
  }
  for (const m of props.members) {
    if (!q || m.name.toLowerCase().includes(q)) {
      results.push({kind: 'member', data: m})
    }
  }
  return results.slice(0, 10)
})

function suggestionKey(s: Suggestion): string {
  if (s.kind === 'member') return `m-${s.data.id}`
  if (s.kind === 'group') return `g-${s.data.id}`
  return `s-${s.data.type}-${s.data.entityId}`
}

function rawToHtml(raw: string): string {
  if (!raw) return ''
  const escaped = raw
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
  // Bulk mention format: @[type:Name:id]
  let result = escaped.replace(/@\[(GROUP|EVENT|REGISTERED|DECLINED):([^:]+):(\d+)]/g, (_match, type, name, id) => {
    return `<span contenteditable="false" data-mention-type="${type}" data-mention-name="${name.replace(/"/g, '&quot;')}" data-mention-id="${id}" class="mention-chip bulk-mention">@${name}</span>`
  })
  // New format: @[stationUid/memberUid:Name]
  result = result.replace(/@\[([^/]+)\/([^:]+):([^\]]+)]/g, (_match, stationUid, memberUid, name) => {
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
      if (element.dataset.mentionType) {
        const type = element.dataset.mentionType
        const name = element.dataset.mentionName
        const id = element.dataset.mentionId
        result += `@[${type}:${name}:${id}]`
      } else if (element.dataset.mentionMember) {
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

function insertChip(chipEl: HTMLSpanElement, displayName: string) {
  const editor = editorRef.value
  const node = mentionStartNode.value
  if (!editor || !node || node.nodeType !== Node.TEXT_NODE) return

  const text = node.textContent ?? ''
  const sel = window.getSelection()
  const cursor = sel?.getRangeAt(0).startOffset ?? text.length

  const before = text.substring(0, mentionStart.value)
  const after = text.substring(cursor)

  chipEl.contentEditable = 'false'
  chipEl.textContent = `@${displayName}`

  const parent = node.parentNode!
  const beforeNode = document.createTextNode(before)
  const spaceAfterNode = document.createTextNode(' ' + after)

  parent.insertBefore(beforeNode, node)
  parent.insertBefore(chipEl, node)
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

function selectSuggestion(s: Suggestion) {
  const chip = document.createElement('span')
  chip.className = 'mention-chip'

  if (s.kind === 'member') {
    const name = s.data.name.trim() || `#${s.data.id}`
    chip.dataset.mentionStation = s.data.stationUid
    chip.dataset.mentionMember = s.data.memberUid
    chip.dataset.mentionName = name
    insertChip(chip, name)
  } else if (s.kind === 'group') {
    const name = s.data.name ?? ''
    chip.className = 'mention-chip bulk-mention'
    chip.dataset.mentionType = 'GROUP'
    chip.dataset.mentionName = name
    chip.dataset.mentionId = String(s.data.id)
    insertChip(chip, name)
  } else {
    chip.className = 'mention-chip bulk-mention'
    chip.dataset.mentionType = s.data.type
    chip.dataset.mentionName = s.data.label
    chip.dataset.mentionId = String(s.data.entityId)
    insertChip(chip, s.data.label)
  }
}

function onKeydown(e: KeyboardEvent) {
  if (showDropdown.value) {
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      selectedIndex.value = Math.min(selectedIndex.value + 1, filteredSuggestions.value.length - 1)
      return
    }
    if (e.key === 'ArrowUp') {
      e.preventDefault()
      selectedIndex.value = Math.max(selectedIndex.value - 1, 0)
      return
    }
    if (e.key === 'Enter') {
      e.preventDefault()
      const s = filteredSuggestions.value[selectedIndex.value]
      if (s) selectSuggestion(s)
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
        v-if="showDropdown && filteredSuggestions.length > 0"
        :style="{ top: dropdownTop + 'px', left: dropdownLeft + 'px' }"
        class="absolute z-30 w-72 max-h-52 overflow-y-auto rounded-theme border border-bg-light-accent bg-bg-light shadow-lg dark:border-bg-dark-accent dark:bg-bg-dark"
    >
      <button
          v-for="(s, i) in filteredSuggestions"
          :key="suggestionKey(s)"
          type="button"
          class="w-full px-3 py-1.5 text-left text-sm transition-colors flex items-center gap-2"
          :class="i === selectedIndex ? 'bg-primary/15 text-primary' : 'hover:bg-primary/10'"
          @mousedown.prevent="selectSuggestion(s)"
          @mouseenter="selectedIndex = i"
      >
        <!-- Member -->
        <template v-if="s.kind === 'member'">
          <UserAvatar :identity="{ stationUid: s.data.stationUid, memberUid: s.data.memberUid }" :name="s.data.name" size="sm" />
          <span class="font-medium truncate" :style="s.data.nameColor ? { color: s.data.nameColor } : {}">{{ s.data.name }}</span>
          <span v-if="s.data.displayTag"
                class="inline-flex items-center rounded-full px-1.5 py-0.5 text-[10px] font-medium leading-none shrink-0"
                :style="{ backgroundColor: s.data.displayTag.color + '20', color: s.data.displayTag.color }">
            {{ s.data.displayTag.name }}
          </span>
        </template>
        <!-- Group -->
        <template v-else-if="s.kind === 'group'">
          <div class="h-6 w-6 shrink-0 rounded-full flex items-center justify-center"
               :style="{ backgroundColor: (s.data.color ?? 'var(--secondary)') + '20', color: s.data.color ?? 'var(--secondary)' }">
            <font-awesome-icon :icon="['fas', 'layer-group']" class="h-3 w-3" />
          </div>
          <span class="font-medium truncate">{{ s.data.name }}</span>
          <span class="text-[10px] text-(--text-muted) shrink-0">{{ t('comments.mentionGroup') }}</span>
        </template>
        <!-- Special -->
        <template v-else>
          <div class="h-6 w-6 shrink-0 rounded-full bg-primary/15 text-primary flex items-center justify-center">
            <font-awesome-icon :icon="s.data.icon" class="h-3 w-3" />
          </div>
          <span class="font-medium truncate">{{ s.data.label }}</span>
        </template>
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

.mention-editor :deep(.bulk-mention) {
  color: var(--secondary);
}
</style>
