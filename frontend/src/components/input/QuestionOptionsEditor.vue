/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script generic="T" lang="ts" setup>
import {useI18n} from 'vue-i18n'
import DragList from './DragList.vue'
import TextInput from './text/TextInput.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import {moveWithin} from '@/util/reorder'

/**
 * The answers a choice question offers, written one to a line of its own.
 *
 * <p>Every feature that asks a choice used to write this again, and each wrote it as one box of text
 * that was split up afterwards: one of them split on line breaks while its own words asked for
 * commas, so a station typing "A, B, C" got a single option called "A, B, C". A row per option
 * cannot be misread, and the order is the reader's, so it is moved the way every other list in Ember
 * is moved.
 *
 * <p>An option is a word by default and anything else where a feature says so: a quiz option carries
 * whether it is the right answer, and an equipment option carries the value stored beside the label.
 * Those live in the slots beside the text rather than in four copies of the list around them.
 */
const options = defineModel<T[]>({required: true})

const props = defineProps<{
  label?: string
  hint?: string
  /** What the button that adds a row says, where the plain wording does not fit. */
  addLabel?: string
  /** The words of an option, where an option is more than its words. */
  textOf?: (option: T) => string
  /** The option with its words changed, where an option is more than its words. */
  withText?: (option: T, text: string) => T
  /** What a fresh option is, where an option is more than its words. */
  blank?: () => T
}>()

const {t} = useI18n()

function wordsOf(option: T): string {
  return props.textOf ? props.textOf(option) : String(option ?? '')
}

function set(index: number, text: string) {
  options.value = options.value.map((option, at) =>
      at === index ? (props.withText ? props.withText(option, text) : (text as T)) : option)
}

function add() {
  options.value = [...options.value, props.blank ? props.blank() : ('' as T)]
}

function remove(index: number) {
  options.value = options.value.filter((_, at) => at !== index)
}
</script>

<template>
  <div class="space-y-1">
    <FieldLabel v-if="label">{{ label }}</FieldLabel>

    <DragList :items="options" :key-fn="(_, index) => index"
              @reorder="(from, to) => options = moveWithin(options, from, to)">
      <template #default="{item, index}">
        <div class="flex items-center gap-2 flex-1">
          <slot :index="index" :option="item" name="before"/>
          <TextInput
              :data-testid="`question-option-${index}`"
              :model-value="wordsOf(item)"
              :placeholder="t('questionOptions.placeholder')"
              class="flex-1"
              @update:model-value="value => set(index, String(value ?? ''))"
          />
          <slot :index="index" :option="item" name="after"/>
          <DeleteButton :data-testid="`question-option-remove-${index}`" @click="remove(index)"/>
        </div>
      </template>
    </DragList>

    <div class="flex flex-wrap items-center gap-2">
      <SecondaryButton :icon="['fas', 'plus']" class="text-sm" data-testid="question-option-add" @click="add">
        {{ addLabel ?? t('questionOptions.add') }}
      </SecondaryButton>
      <slot name="actions"/>
    </div>

    <p v-if="hint" class="text-xs text-(--text-muted)">{{ hint }}</p>
  </div>
</template>
