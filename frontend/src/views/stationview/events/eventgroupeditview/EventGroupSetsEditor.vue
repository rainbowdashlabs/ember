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
import MutedText from '@/components/typography/MutedText.vue'
import {moveWithin} from '@/util/reorder'
import type {SetRow} from './eventGroupSetRow'

/**
 * The sets of an event group, in the order members see them: a name and how many of its parts a sign-up
 * has to choose. An event group always keeps at least one set. A set holding parts asks for none to all
 * of them; a set without parts yet takes any minimum, which counts once parts come, and a set still
 * filling up keeps the minimum it was saved with, as the server does. A number outside that is taken
 * back to the nearest allowed one, and the box shows it once the reader leaves it.
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
function resync(event: FocusEvent, set: SetRow) {
  const input = event.target as HTMLInputElement | null
  if (input && input.value !== String(set.minimum)) input.value = String(set.minimum)
}

function add() {
  sets.value = [...sets.value, {id: null, name: '', minimum: 1}]
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
    <DragList :items="sets" :key-fn="(set, index) => set.id ?? `new-${index}`" @reorder="(from, to) => sets = moveWithin(sets, from, to)">
      <template #default="{item: set, index}">
        <div class="flex flex-wrap items-center gap-2">
          <TextInput
              :model-value="set.name"
              :aria-label="t('eventGroups.setName')"
              class="flex-1 min-w-40"
              @update:model-value="value => update(index, {name: value ?? ''})"
          />
          <span class="w-24" @focusout="event => resync(event, set)">
            <NumberInput
                :model-value="set.minimum"
                :aria-label="t('eventGroups.setMinimum')"
                min="0"
                :data-testid="`event-group-set-minimum-${index}`"
                @update:model-value="value => update(index, {minimum: bounded(set, value)})"
            />
          </span>
          <MutedText tag="span" size="sm" :data-testid="`event-group-set-hint-${index}`">{{ hint(set) }}</MutedText>
          <DeleteButton v-if="sets.length > 1" :label="t('eventGroups.removeSet')" @click="remove(index)"/>
        </div>
      </template>
    </DragList>
    <SecondaryButton :icon="['fas', 'plus']" data-testid="event-group-add-set" @click="add">{{ t('eventGroups.addSet') }}</SecondaryButton>
  </div>
</template>
