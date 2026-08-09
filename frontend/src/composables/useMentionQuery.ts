/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, nextTick, type Ref, ref} from 'vue'
import type {MemberCompletion} from '@/api/stationMembers'
import type {MemberGroup} from '@/api/types'

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

export interface MentionSources {
  members: MemberCompletion[]
  groups: MemberGroup[]
  specialMentions: SpecialMention[]
}

/**
 * Owns the caret driven mention state machine of the mention editor: trigger
 * detection, dropdown placement, keyboard navigation and chip insertion.
 *
 * All caret arithmetic lives here so the editor component only has to render
 * the suggestions this returns.
 */
export function useMentionQuery(
    editorRef: Ref<HTMLDivElement | null>,
    sources: MentionSources,
    onInserted: () => void,
) {
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

    for (const s of sources.specialMentions) {
      if (!q || s.label.toLowerCase().includes(q)) {
        results.push({kind: 'special', data: s})
      }
    }
    for (const g of sources.groups) {
      if (g.name && (!q || g.name.toLowerCase().includes(q))) {
        results.push({kind: 'group', data: g})
      }
    }
    for (const m of sources.members) {
      if (!q || m.name.toLowerCase().includes(q)) {
        results.push({kind: 'member', data: m})
      }
    }
    return results.slice(0, 10)
  })

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
    const spaceAfterNode = document.createTextNode(' ' + after)

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
    onInserted()
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

  function handleKeydown(e: KeyboardEvent) {
    if (!showDropdown.value) return
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
    }
  }

  return {
    showDropdown,
    filteredSuggestions,
    selectedIndex,
    dropdownTop,
    dropdownLeft,
    checkForMention,
    selectSuggestion,
    handleKeydown,
  }
}
