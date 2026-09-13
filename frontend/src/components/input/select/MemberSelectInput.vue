/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, nextTick, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import MemberName from '@/components/avatar/MemberName.vue'
import MutedText from '@/components/typography/MutedText.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import MemberMenuChips from './membermenu/MemberMenuChips.vue'
import MemberMenuRow from './membermenu/MemberMenuRow.vue'
import MemberMenuSearch from './membermenu/MemberMenuSearch.vue'
import {useFinePointer} from '@/composables/useFinePointer'
import {useMemberOptions} from './membermenu/useMemberOptions'
import type {MemberIdentity} from '@/api/types'
import {identityOf, type MemberOption} from './memberOption'

/**
 * The one way to pick a member.
 *
 * <p>Every menu that asks "which member?" is this one, single choice and multiple alike, because the two
 * differ in what a click does and in nothing else. A row is a face, a name in whatever colour the member's
 * group gives it, and their tag, so somebody is recognised by their picture long before they are found by
 * reading. There is always a search, with no threshold and no way to switch it off: a menu that looks
 * different in different places is how six of them came to exist.
 *
 * <p>The keyboard is the fast path. Down and up walk the rows, Home and End jump to the ends, Enter takes
 * the highlighted one and Escape closes without taking anything, so the common case is three letters and
 * Enter. Typing resets the highlight to the first match, which is what makes that work.
 *
 * <p>There are two model names because the type of the answer depends on {@code multiple}: a single choice
 * is a value and a multiple one is a list of them, and one model cannot be both without every call site
 * losing its type. A single menu binds {@code v-model}, a multiple menu binds {@code v-model:selected}.
 */
const model = defineModel<string>({default: ''})
const selected = defineModel<string[]>('selected', {default: () => []})

const props = withDefaults(defineProps<{
  /** The people on offer, where the caller holds them. Ignored when {@code searchFn} is given. */
  members?: MemberOption[]
  /** Asks the server instead of holding the list. Called with what was typed, and with the empty string on open. */
  searchFn?: (query: string) => Promise<MemberOption[]>
  /** Puts a name to a value the fetched list does not happen to contain, so a stored choice still reads. */
  resolveFn?: (value: string) => Promise<MemberOption | null>
  /** Whether a click adds to a list rather than replacing the choice. */
  multiple?: boolean
  /** Whether "nobody" is an answer. Off by default, because most menus must take somebody. */
  clearable?: boolean
  /** What the empty answer is called, where "nobody" is not what it means. A claim made for oneself is one. */
  emptyLabel?: string
  /** The kinds of member to offer beside the search. Fewer than two offers nothing. */
  userTypes?: string[]
  /** The kind the filter opens on, where one kind is what the screen is really asking for. */
  openingUserType?: string
  placeholder?: string
  disabled?: boolean
  autoOpen?: boolean
}>(), {
  members: () => [],
  multiple: false,
  clearable: false,
  userTypes: () => [],
  openingUserType: '',
})

const emit = defineEmits<{
  change: []
}>()

const {t} = useI18n()
const {finePointer} = useFinePointer()

const open = ref(false)
const search = ref('')
const userType = ref(props.openingUserType)
const highlighted = ref(0)
const containerRef = ref<HTMLElement | null>(null)
const searchRef = ref<InstanceType<typeof MemberMenuSearch> | null>(null)
const listRef = ref<HTMLElement | null>(null)

const values = computed(() => (props.multiple ? selected.value : model.value ? [model.value] : []))
const chosen = computed(() => new Set(values.value))

const {matching, selectedOptions, fetching, optionFor, refresh} = useMemberOptions({
  members: () => props.members,
  searchFn: () => props.searchFn,
  resolveFn: () => props.resolveFn,
  search: () => search.value,
  userType: () => userType.value,
  chosenValues: () => values.value,
})

/** The empty answer is a row like any other, so the keyboard reaches it without knowing it is special. */
const rows = computed<Array<MemberOption | null>>(() =>
    (props.clearable && !props.multiple ? [null, ...matching.value] : matching.value))

const triggerLabel = computed(() => {
  if (props.multiple) {
    if (selected.value.length === 0) return props.placeholder ?? t('memberSelect.choose')
    return t('memberSelect.chosenCount', {count: selected.value.length})
  }
  if (!model.value) return props.clearable && props.emptyLabel ? props.emptyLabel : props.placeholder ?? t('memberSelect.choose')
  return optionFor(model.value)?.name ?? props.placeholder ?? t('memberSelect.choose')
})

const triggerIdentity = computed<MemberIdentity | null>(() => {
  if (props.multiple || !model.value) return null
  const option = optionFor(model.value)
  return option ? identityOf(option) : null
})

/** Anything that changes what is on offer puts the highlight back on the first row. */
watch([search, userType, matching], () => {
  highlighted.value = 0
})

watch(open, async isOpen => {
  if (!isOpen) {
    search.value = ''
    return
  }
  highlighted.value = 0
  await refresh()
  if (finePointer.value) await nextTick(() => searchRef.value?.focus())
})

function toggle(option: MemberOption) {
  if (chosen.value.has(option.value)) {
    selected.value = selected.value.filter(value => value !== option.value)
  } else {
    selected.value = [...selected.value, option.value]
  }
  emit('change')
}

function take(option: MemberOption | null) {
  if (props.multiple) {
    if (option) toggle(option)
    return
  }
  model.value = option?.value ?? ''
  open.value = false
  emit('change')
}

function removeChip(option: MemberOption) {
  if (props.multiple) {
    selected.value = selected.value.filter(value => value !== option.value)
  } else {
    model.value = ''
  }
  emit('change')
}

function highlight(index: number) {
  highlighted.value = index
  void nextTick(() => {
    listRef.value?.querySelectorAll('[data-row]')[highlighted.value]?.scrollIntoView({block: 'nearest'})
  })
}

/** Walking past either end comes round to the other, so a long list is reached from whichever end is nearer. */
function moveHighlight(delta: number) {
  const total = rows.value.length
  if (total > 0) highlight((highlighted.value + delta + total) % total)
}

function takeHighlighted() {
  if (rows.value.length > 0) take(rows.value[highlighted.value] ?? null)
}

/** Closing gives the focus back to the trigger, so the reader is where they left off rather than nowhere. */
function close() {
  open.value = false
  containerRef.value?.querySelector<HTMLElement>('[data-trigger]')?.focus()
}

/** What each key does once the menu is open. Anything else is left to the browser. */
const KEYS: Record<string, () => void> = {
  ArrowDown: () => moveHighlight(1),
  ArrowUp: () => moveHighlight(-1),
  Home: () => highlight(0),
  End: () => highlight(Math.max(0, rows.value.length - 1)),
  Enter: takeHighlighted,
  Escape: close,
}

/** A closed menu answers only to the two keys that open it, which is how it is reached without a mouse. */
const OPENING_KEYS = ['Enter', 'ArrowDown']

function onKeydown(event: KeyboardEvent) {
  const act = open.value ? KEYS[event.key] : OPENING_KEYS.includes(event.key) ? () => (open.value = true) : undefined
  if (!act) return
  event.preventDefault()
  act()
}

function onClickOutside(event: MouseEvent) {
  if (containerRef.value && !containerRef.value.contains(event.target as Node)) open.value = false
}

onMounted(() => {
  document.addEventListener('click', onClickOutside)
  if (props.autoOpen) open.value = true
})

onBeforeUnmount(() => document.removeEventListener('click', onClickOutside))
</script>

<template>
  <div ref="containerRef" class="relative space-y-2" @keydown="onKeydown">
    <MemberMenuChips
        v-if="multiple"
        :options="selectedOptions"
        :disabled="disabled"
        @remove="removeChip"
    />

    <button
        type="button"
        data-trigger
        data-testid="member-select-trigger"
        :disabled="disabled"
        :aria-expanded="open"
        class="flex w-full items-center gap-2 rounded-theme border border-(--border) bg-(--bg) px-3 py-2
               text-left text-sm transition-colors hover:border-primary disabled:opacity-50"
        @click="open = !open"
    >
      <MemberName v-if="triggerIdentity" :identity="triggerIdentity" class="min-w-0 flex-1"/>
      <span v-else class="flex-1 truncate" :class="values.length === 0 ? 'text-(--text-muted)' : ''">
        {{ triggerLabel }}
      </span>
      <font-awesome-icon :icon="['fas', 'chevron-down']" class="shrink-0 text-xs text-(--text-muted)"/>
    </button>

    <div
        v-if="open"
        data-testid="member-select-panel"
        class="absolute left-0 right-0 top-full z-20 mt-1 flex max-h-80 flex-col overflow-hidden
               rounded-theme border border-(--border) bg-(--bg) shadow-lg"
    >
      <MemberMenuSearch
          ref="searchRef"
          v-model:search="search"
          v-model:user-type="userType"
          :user-types="userTypes"
      />

      <div ref="listRef" class="overflow-y-auto py-1">
        <div v-if="fetching" class="flex justify-center py-3">
          <Spinner size="sm"/>
        </div>
        <MutedText v-else-if="rows.length === 0" tag="div" size="sm" class="px-3 py-2">
          {{ t('memberSelect.nobodyMatches') }}
        </MutedText>
        <MemberMenuRow
            v-for="(row, index) in rows"
            v-else
            :key="row?.value ?? 'nobody'"
            :option="row"
            :highlighted="index === highlighted"
            :multiple="multiple"
            :chosen="!!row && chosen.has(row.value)"
            :empty-label="emptyLabel"
            @take="take(row)"
            @hover="highlighted = index"
        />
      </div>
    </div>
  </div>
</template>
