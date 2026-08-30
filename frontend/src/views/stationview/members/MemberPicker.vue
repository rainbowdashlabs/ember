/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onBeforeUnmount, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import IconButton from '@/components/button/IconButton.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {StationUserTypeLabels} from '@/api/types'
import type {MemberIdentity} from '@/api/types'

/** Somebody who can be picked, with whatever is known about them. */
export interface PickableMember {
    id: number
    name: string
    email?: string | null
    identity?: MemberIdentity | null
    userType?: string | null
}

/**
 * Picking one person out of the station.
 *
 * <p>A list of people with their faces and their names, the way a group shows its members, rather
 * than a native dropdown of three hundred lines of text. Somebody is recognised by their picture
 * long before they are found by scrolling.
 *
 * <p>The list keeps out of the way until it is wanted: it opens as soon as anything is typed, and
 * otherwise waits behind the arrow. A picker that is permanently unfolded pushes everything below it
 * off the screen, and one that only opens on typing cannot be browsed by somebody who does not know
 * the name they are looking for.
 */
const props = defineProps<{
  members: PickableMember[]
  /** The kinds present among them, for the filter beside the search. Fewer than two offers nothing. */
  userTypes: string[]
  placeholder?: string
}>()

const emit = defineEmits<{
  select: [memberId: number]
}>()

const {t} = useI18n()

const containerRef = ref<HTMLElement | null>(null)
const search = ref('')
const userType = ref('')
const expanded = ref(false)

/** Typing opens the list; the arrow opens it without typing, for somebody who wants to browse. */
const open = computed(() => expanded.value || search.value.trim().length > 0)

/** By name, so a reader looking for somebody can run down the list instead of reading all of it. */
const byName = new Intl.Collator('de', {sensitivity: 'base'})

const matching = computed(() => {
  const needle = search.value.trim().toLowerCase()
  return props.members
      .filter(member => !userType.value || member.userType === userType.value)
      .filter(member => !needle
          || member.name.toLowerCase().includes(needle)
          || (member.email ?? '').toLowerCase().includes(needle))
      .toSorted((one, other) => byName.compare(one.name, other.name))
})

function typeLabel(value: string): string {
  return StationUserTypeLabels[value as keyof typeof StationUserTypeLabels] ?? value
}

function pick(memberId: number) {
  emit('select', memberId)
  search.value = ''
  expanded.value = false
}

function onClickOutside(event: MouseEvent) {
  if (containerRef.value && !containerRef.value.contains(event.target as Node)) {
    expanded.value = false
    search.value = ''
  }
}

onMounted(() => document.addEventListener('click', onClickOutside))
onBeforeUnmount(() => document.removeEventListener('click', onClickOutside))
</script>

<template>
  <div ref="containerRef" class="space-y-2">
    <div class="flex flex-wrap items-center gap-2">
      <TextInput
          v-model="search"
          data-testid="member-picker-search"
          class="min-w-40 flex-1"
          :placeholder="placeholder ?? t('memberGroups.searchMembers')"
      />
      <SelectInput v-if="userTypes.length > 1" v-model="userType" data-testid="member-picker-type" class="w-44">
        <option value="">{{ t('memberGroups.anyUserType') }}</option>
        <option v-for="value in userTypes" :key="value" :value="value">{{ typeLabel(value) }}</option>
      </SelectInput>
      <IconButton
          :icon="['fas', open ? 'chevron-up' : 'chevron-down']"
          :label="t(open ? 'memberGroups.hideList' : 'memberGroups.showList')"
          data-testid="member-picker-toggle"
          @click="expanded = !expanded"
      />
    </div>

    <div
        v-if="open"
        data-testid="member-picker-list"
        class="max-h-64 space-y-1 overflow-y-auto rounded-lg border border-(--border) p-1"
    >
      <MutedText v-if="matching.length === 0" tag="div" size="sm" class="px-3 py-2">
        {{ t('memberGroups.nobodyMatches') }}
      </MutedText>
      <button
          v-for="member in matching"
          :key="member.id"
          type="button"
          data-testid="member-picker-option"
          class="flex w-full items-center justify-between gap-2 rounded-lg px-3 py-2 text-left
                 transition-colors hover:bg-bg-light-accent dark:hover:bg-bg-dark-accent"
          @click="pick(member.id)"
      >
        <span class="flex min-w-0 items-center gap-2">
          <MemberName v-if="member.identity" :identity="member.identity" class="text-sm font-medium"/>
          <span v-else class="truncate text-sm font-medium">{{ member.name }}</span>
          <MutedText v-if="member.email" size="sm" class="truncate">{{ member.email }}</MutedText>
        </span>
        <font-awesome-icon :icon="['fas', 'plus']" class="shrink-0 text-primary text-sm"/>
      </button>
    </div>
  </div>
</template>
