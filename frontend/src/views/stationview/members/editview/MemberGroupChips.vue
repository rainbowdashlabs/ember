/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SectionLabel from '@/components/typography/SectionLabel.vue'
import type {MemberGroup} from '@/api/types'
import type {MemberGroupSet} from '@/api/groupSets'
import {
  admits,
  boundTypeNames,
  chooseInSet,
  groupsOutsideSets,
  setsWithGroups,
  toggled,
  type SetOfGroups,
} from '@/util/groupRules'

/**
 * The groups of one member as a compact row of chips.
 *
 * <p>A group in no set is joined and left on its own. The groups of a set are one choice, with "none"
 * beside them, because a member can be in only one of them and picking another is a move. A group bound
 * to member types the member does not have stays visible but cannot be picked, and says which types it
 * takes, so nobody wonders why it will not light up.
 */
const props = defineProps<{
  groups: MemberGroup[]
  sets: MemberGroupSet[]
  selected: ReadonlySet<number>
  userType: string
  disabled?: boolean
}>()

const emit = defineEmits<{
  change: [groupIds: Set<number>]
}>()

const {t} = useI18n()

const looseGroups = computed(() => groupsOutsideSets(props.groups))
const choices = computed(() => setsWithGroups(props.groups, props.sets))

function closed(group: MemberGroup): boolean {
  return !props.selected.has(group.id) && !admits(group, props.userType)
}

function boundTo(group: MemberGroup): string {
  return t('memberEdit.groupBoundTo', {types: boundTypeNames(group)})
}

const closedGroups = computed(() => props.groups.filter(closed))

/** One chip of a set's choice: "none" first, then each group of the set. */
interface SetOption {
  key: string
  groupId: number | null
  label: string
  chosen: boolean
  closed: boolean
  hint?: string
}

function optionsOf(entry: SetOfGroups): SetOption[] {
  const none: SetOption = {
    key: 'none',
    groupId: null,
    label: t('memberEdit.groupSetNone'),
    chosen: !entry.groups.some(group => props.selected.has(group.id)),
    closed: false,
  }
  return [none, ...entry.groups.map(group => ({
    key: String(group.id),
    groupId: group.id,
    label: group.name ?? '',
    chosen: props.selected.has(group.id),
    closed: closed(group),
    hint: closed(group) ? boundTo(group) : undefined,
  }))]
}
</script>

<template>
  <div class="space-y-3">
    <div v-if="looseGroups.length > 0" class="flex flex-wrap gap-2">
      <SelectionToggleButton
          v-for="group in looseGroups"
          :key="group.id"
          :selected="selected.has(group.id)"
          :disabled="disabled || closed(group)"
          :title="closed(group) ? boundTo(group) : undefined"
          @toggle="emit('change', toggled(selected, group.id))"
      >
        {{ group.name }}
      </SelectionToggleButton>
    </div>

    <div v-for="entry in choices" :key="entry.set.id" class="space-y-1">
      <SectionLabel>{{ entry.set.name }}</SectionLabel>
      <div class="flex flex-wrap gap-2">
        <SelectionToggleButton
            v-for="option in optionsOf(entry)"
            :key="option.key"
            :selected="option.chosen"
            :disabled="disabled || option.closed"
            :title="option.hint"
            @toggle="emit('change', chooseInSet(selected, entry, option.groupId))"
        >
          {{ option.label }}
        </SelectionToggleButton>
      </div>
    </div>

    <MutedText v-if="groups.length === 0" tag="p">{{ t('memberEdit.noGroups') }}</MutedText>
    <MutedText v-for="group in closedGroups" :key="group.id" tag="p">
      {{ group.name }}: {{ boundTo(group) }}
    </MutedText>
  </div>
</template>
