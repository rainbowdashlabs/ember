/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import type {AcceptableValue} from 'reka-ui'
import DropdownListbox from '@/components/input/select/dropdown/DropdownListbox.vue'
import DropdownOption from '@/components/input/select/dropdown/DropdownOption.vue'
import FontChoiceEntry from './FontChoiceEntry.vue'
import type {FontEntry} from './fontOptions'

/**
 * The list a font picker opens: every font it offers with its name, its origin and a line of sample
 * text, the current one marked. It is a listbox, so the keyboard walks it, Enter takes an entry and
 * typing jumps to a family by its first letters.
 */
const model = defineModel<string>({required: true})

defineProps<{
  entries: readonly FontEntry[]
  /** What the list is, for a screen reader. */
  label: string
  /** What prints in place of a family that is no longer reached. */
  missingNote: string
}>()

function take(value: AcceptableValue | AcceptableValue[] | undefined) {
  model.value = String(value ?? '')
}
</script>

<template>
  <DropdownListbox :model-value="model" :label="label" @update:model-value="take">
    <DropdownOption v-for="entry in entries" :key="entry.value" :value="entry.value" data-testid="font-entry"
                    class="items-start data-[state=checked]:text-primary">
      <font-awesome-icon :icon="['fas', 'check']"
                         :class="['mt-1 h-3 w-3 shrink-0 text-primary', entry.value === model ? '' : 'invisible']"/>
      <FontChoiceEntry :entry="entry" :missing-note="missingNote"/>
    </DropdownOption>
  </DropdownListbox>
</template>
