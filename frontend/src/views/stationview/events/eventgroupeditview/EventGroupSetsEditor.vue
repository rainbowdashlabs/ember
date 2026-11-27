/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import DragList from '@/components/input/DragList.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import HintIcon from '@/components/typography/HintIcon.vue'
import {moveWithin} from '@/util/reorder'
import {DRAG_CONTROL_COLUMN} from '@/components/input/dragControls'
import type {SetRow} from './eventGroupSetRow'

/** The width of the minimum and the maximum, in the header and in every row alike. */
const NUMBER_COLUMN = 'w-24 shrink-0'

/** The width of the delete button, kept as an empty column where a lone set cannot be removed, so the header lines up. */
const ACTION_COLUMN = 'w-8 shrink-0'

/** The width of the info icon that shows what the set's numbers mean, kept empty where there is nothing to show. */
const HINT_COLUMN = 'w-5 shrink-0'

/**
 * The sets of an event group, in the order members see them: a name and how many of its parts a sign-up
 * has to choose. An event group always keeps at least one set. A set holding parts asks for none to all
 * of them; a set without parts yet takes any minimum, which counts once parts come, and a set still
 * filling up keeps the minimum it was saved with, as the server does. A number outside that is taken
 * back to the nearest allowed one, and the box shows it once the reader leaves it.
 *
 * <p>A set may also hold a maximum, "höchstens", which is empty for none. It is at least 1 and at
 * least the minimum: a maximum typed below that is taken up to it, and a minimum raised past the
 * maximum takes the maximum with it. "At least 1, at most 1" makes the parts of a set alternatives.
 */
const sets = defineModel<SetRow[]>({required: true})
const props = withDefaults(defineProps<{
  /** How many parts each saved set holds, by set id. */
  partCounts?: Readonly<Record<number, number>>
  /** The minimum each saved set was stored with, by set id. */
  storedMinimums?: Readonly<Record<number, number>>
}>(), {
  partCounts: () => ({}),
  storedMinimums: () => ({}),
})
const {t} = useI18n()

function partsOf(set: SetRow): number {
  return set.id == null ? 0 : props.partCounts[set.id] ?? 0
}

function bounded(set: SetRow, value: number | undefined): number {
  const parts = partsOf(set)
  const wanted = Math.max(0, Math.trunc(value ?? 0))
  if (parts === 0 || wanted <= parts) return wanted
  return set.id != null && props.storedMinimums[set.id] === wanted ? wanted : parts
}

function hint(set: SetRow): string {
  const parts = partsOf(set)
  if (parts === 0) return t('eventGroups.setMinimumPending')
  if (set.minimum > parts) return t('eventGroups.setMinimumFilling', {count: parts, minimum: set.minimum})
  return t('eventGroups.setMinimumOf', {count: parts})
}

/** Shows the number the set holds once the reader leaves the box, where a typed one was taken back. */
function resync(event: FocusEvent, value: number | null) {
  const input = event.target as HTMLInputElement | null
  const shown = value == null ? '' : String(value)
  if (input && input.value !== shown) input.value = shown
}

/** A new minimum, with the maximum raised along where the minimum passes it. */
function withMinimum(set: SetRow, value: number | undefined): Partial<SetRow> {
  const minimum = bounded(set, value)
  return set.maximum != null && set.maximum < minimum ? {minimum, maximum: minimum} : {minimum}
}

/** A maximum as typed: empty or zero for none, otherwise at least 1 and at least the minimum. */
function boundedMaximum(set: SetRow, value: number | undefined): number | null {
  if (!value) return null
  return Math.max(Math.trunc(value), set.minimum, 1)
}

function add() {
  sets.value = [...sets.value, {id: null, name: '', minimum: 1, maximum: null}]
}

function remove(index: number) {
  sets.value = sets.value.filter((_, at) => at !== index)
}

function update(index: number, change: Partial<SetRow>) {
  sets.value = sets.value.map((set, at) => (at === index ? {...set, ...change} : set))
}
</script>

<template>
  <div class="space-y-2" data-testid="event-group-sets">
    <div class="hidden sm:flex items-end gap-2 text-xs font-medium text-(--text-muted)" data-testid="event-group-sets-header">
      <span :class="[DRAG_CONTROL_COLUMN, 'shrink-0']" aria-hidden="true"/>
      <span class="flex-1 min-w-40">{{ t('eventGroups.setName') }}</span>
      <span :class="NUMBER_COLUMN">{{ t('eventGroups.setMinimumColumn') }}</span>
      <span :class="NUMBER_COLUMN">{{ t('eventGroups.setMaximumColumn') }}</span>
      <span :class="HINT_COLUMN" aria-hidden="true"/>
      <span :class="ACTION_COLUMN" aria-hidden="true"/>
    </div>
    <DragList :items="sets" :key-fn="(set, index) => set.id ?? `new-${index}`" @reorder="(from, to) => sets = moveWithin(sets, from, to)">
      <template #default="{item: set, index}">
        <div>
          <div class="flex flex-wrap items-end gap-2 sm:flex-nowrap sm:items-center">
            <TextInput
                :model-value="set.name"
                :aria-label="t('eventGroups.setName')"
                class="flex-1 min-w-40"
                @update:model-value="value => update(index, {name: value ?? ''})"
            />
            <span :class="[NUMBER_COLUMN, 'flex flex-col gap-0.5']" @focusout="event => resync(event, set.minimum)">
              <span class="text-xs text-(--text-muted) sm:hidden" aria-hidden="true">{{ t('eventGroups.setMinimumColumn') }}</span>
              <NumberInput
                  :model-value="set.minimum"
                  :aria-label="t('eventGroups.setMinimum')"
                  min="0"
                  :data-testid="`event-group-set-minimum-${index}`"
                  @update:model-value="value => update(index, withMinimum(set, value))"
              />
            </span>
            <span :class="[NUMBER_COLUMN, 'flex flex-col gap-0.5']" @focusout="event => resync(event, set.maximum)">
              <span class="text-xs text-(--text-muted) sm:hidden" aria-hidden="true">{{ t('eventGroups.setMaximumColumn') }}</span>
              <NumberInput
                  :model-value="set.maximum ?? undefined"
                  :aria-label="t('eventGroups.setMaximum')"
                  :placeholder="t('eventGroups.setMaximumNone')"
                  min="0"
                  :data-testid="`event-group-set-maximum-${index}`"
                  @update:model-value="value => update(index, {maximum: boundedMaximum(set, value)})"
              />
            </span>
            <span :class="[HINT_COLUMN, 'flex items-center']" :data-testid="`event-group-set-hint-${index}`">
              <HintIcon v-if="hint(set)" :text="hint(set)"/>
            </span>
            <span :class="ACTION_COLUMN">
              <DeleteButton v-if="sets.length > 1" :label="t('eventGroups.removeSet')" @click="remove(index)"/>
            </span>
          </div>
        </div>
      </template>
    </DragList>
    <SecondaryButton :icon="['fas', 'plus']" data-testid="event-group-add-set" @click="add">{{ t('eventGroups.addSet') }}</SecondaryButton>
  </div>
</template>
