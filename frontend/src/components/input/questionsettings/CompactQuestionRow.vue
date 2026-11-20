/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import IconButton from '@/components/button/IconButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'

/**
 * A question of a list shown as one line: its name, its type and the marks its list cares about,
 * with the full question card behind a button.
 *
 * <p>A list of full cards asked for every setting of every question at once, which turned a handful
 * of questions into a long page. Most of them only need their name read or changed; the rest of the
 * card opens on request. The list decides what stands beside the name (a value, marks) and what the
 * opened card is.
 *
 * <p>The arrow that opens and closes the card stands at the far end of the line in both states, with the delete
 * button kept apart before it: a second click on the arrow, as in a double click, toggles the card again and never
 * lands on delete.
 */
const open = defineModel<boolean>('open', {required: true})

defineProps<{
  name: string
  /** What the name box is read out as. */
  nameLabel: string
  /** The question's type, as the badge shows it. */
  typeLabel: string
  /** Shown only, as on a help page: nothing on the line can be changed or opened. */
  disabled?: boolean
}>()

const emit = defineEmits<{
  'update:name': [name: string]
  remove: []
}>()

const {t} = useI18n()
</script>

<template>
  <div data-testid="question-row">
    <div v-if="!open" class="flex flex-wrap items-center gap-2">
      <TextInput
          :model-value="name"
          :aria-label="nameLabel"
          :disabled="disabled"
          class="min-w-0 grow basis-32 font-semibold"
          data-testid="question-row-name"
          @update:model-value="value => emit('update:name', String(value ?? ''))"
      />
      <slot name="value"/>
      <div class="flex shrink-0 items-center gap-1">
        <SecondaryBadge data-testid="question-row-type">{{ typeLabel }}</SecondaryBadge>
        <slot name="marks"/>
        <DeleteButton
            :disabled="disabled"
            data-testid="question-row-remove"
            @click="emit('remove')"
        />
        <IconButton
            :icon="['fas', 'chevron-down']"
            :label="t('common.openSettings')"
            :disabled="disabled"
            class="ml-3"
            data-testid="question-row-open"
            @click="open = true"
        />
      </div>
    </div>

    <div v-else class="space-y-1">
      <div class="flex justify-end">
        <IconButton
            :icon="['fas', 'chevron-up']"
            :label="t('common.closeSettings')"
            data-testid="question-row-close"
            @click="open = false"
        />
      </div>
      <slot/>
    </div>
  </div>
</template>
