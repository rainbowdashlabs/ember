/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref, useTemplateRef, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import type {AcceptableValue} from 'reka-ui'
import MemberName from '@/components/avatar/MemberName.vue'
import MutedText from '@/components/typography/MutedText.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import DropdownPanel from './dropdown/DropdownPanel.vue'
import DropdownListbox from './dropdown/DropdownListbox.vue'
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
 * <p>The keyboard is the fast path. Down and up walk the rows and come round at either end, Home and End
 * jump to the ends, Enter takes the highlighted one and Escape closes without taking anything, so the
 * common case is three letters and Enter. Typing resets the highlight to the first match, which is what
 * makes that work. A closed menu opens on Enter or the down arrow.
 *
 * <p>On a touch screen the search is not focused as the menu opens, since that would push the on-screen
 * keyboard over the very rows the reader came to tap.
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
  searchFn: undefined,
  resolveFn: undefined,
  multiple: false,
  clearable: false,
  emptyLabel: undefined,
  userTypes: () => [],
  openingUserType: '',
  placeholder: undefined,
})

const emit = defineEmits<{
  change: []
}>()

const {t} = useI18n()
const {finePointer} = useFinePointer()

const open = ref(false)
const search = ref('')
const userType = ref(props.openingUserType)
const list = useTemplateRef('list')

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
watch([search, userType, matching], () => list.value?.highlightFirst())

watch(open, async isOpen => {
  if (!isOpen) {
    search.value = ''
    return
  }
  await refresh()
})

/** A single menu takes the row and closes; a multiple one has already added or taken it away. */
function take(value: AcceptableValue | AcceptableValue[] | undefined) {
  if (props.multiple) {
    selected.value = Array.isArray(value) ? value.map(String) : []
  } else {
    model.value = String(value ?? '')
    open.value = false
  }
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

function focusSearchOnlyWithAMouse(event: Event) {
  if (!finePointer.value) event.preventDefault()
}

onMounted(() => {
  if (props.autoOpen) open.value = true
})
</script>

<template>
  <div class="space-y-2">
    <MemberMenuChips
        v-if="multiple"
        :options="selectedOptions"
        :disabled="disabled"
        @remove="removeChip"
    />

    <DropdownPanel
        v-model:open="open"
        match-width
        panel-class="max-h-80"
        test-id="member-select-panel"
        @open-auto-focus="focusSearchOnlyWithAMouse"
    >
      <template #trigger>
        <button
            type="button"
            data-trigger
            data-testid="member-select-trigger"
            :disabled="disabled"
            aria-haspopup="listbox"
            class="flex w-full items-center gap-2 rounded-theme border border-(--border) bg-(--bg) px-3 py-2
                   text-left text-sm transition-colors hover:border-primary disabled:opacity-50"
            @keydown.down.prevent="open = true"
        >
          <MemberName v-if="triggerIdentity" :identity="triggerIdentity" class="min-w-0 flex-1"/>
          <span v-else class="flex-1 truncate" :class="values.length === 0 ? 'text-(--text-muted)' : ''">
            {{ triggerLabel }}
          </span>
          <font-awesome-icon :icon="['fas', 'chevron-down']" class="shrink-0 text-xs text-(--text-muted)"/>
        </button>
      </template>

      <DropdownListbox ref="list" :model-value="multiple ? selected : model" :multiple="multiple" :label="triggerLabel" @update:model-value="take">
        <template #head>
          <MemberMenuSearch v-model:search="search" v-model:user-type="userType" :user-types="userTypes"/>
        </template>
        <div v-if="fetching" class="flex justify-center py-3">
          <Spinner size="sm"/>
        </div>
        <MutedText v-else-if="rows.length === 0" tag="div" size="sm" class="px-3 py-2">
          {{ t('memberSelect.nobodyMatches') }}
        </MutedText>
        <MemberMenuRow
            v-for="row in rows"
            v-else
            :key="row?.value ?? 'nobody'"
            :option="row"
            :multiple="multiple"
            :chosen="!!row && chosen.has(row.value)"
            :empty-label="emptyLabel"
        />
      </DropdownListbox>
    </DropdownPanel>
  </div>
</template>
