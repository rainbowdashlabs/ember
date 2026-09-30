/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {contrastTextColor} from '@/util/contrastColor'
import type {SelectableOption} from '../LabelSelectInput.vue'

/**
 * The closed face of a label picker: what is picked, as chips that can each be taken off, and the
 * words not made yet beside them. The whole of it opens the picker, by pointer or by Enter and
 * Space once it has the focus. A disabled picker keeps its place in the tab order and says it is
 * disabled, the way a disabled ARIA button may, and opens nothing. Each chip's cross is a control of
 * its own, reached by pointer or keyboard.
 */
const props = defineProps<{
  selected: SelectableOption[]
  drafts: string[]
  disabled: boolean
  placeholder: string
}>()

const emit = defineEmits<{
  remove: [labelId: number]
  dropDraft: [name: string]
}>()

const {t} = useI18n()

function openFromKeyboard(event: KeyboardEvent) {
  event.preventDefault()
  if (props.disabled) return
  const trigger = event.currentTarget as HTMLElement
  trigger.click()
}
</script>

<template>
  <div
      role="button"
      aria-haspopup="listbox"
      tabindex="0"
      :aria-disabled="disabled"
      class="flex flex-wrap items-center gap-1 min-h-[2rem] px-2 py-1 rounded-theme bg-transparent cursor-pointer transition-colors hover:bg-[var(--bg-accent)]"
      :class="{'opacity-50 pointer-events-none': disabled}"
      data-testid="label-select"
      @keydown.enter.space.self="openFromKeyboard"
  >
    <BaseBadge
        v-for="label in selected"
        :key="label.id"
        :bg-class="label.color ? '' : 'bg-primary/15'"
        class="inline-flex items-center gap-1"
        :style="label.color ? {backgroundColor: label.color, color: contrastTextColor(label.color)} : undefined"
    >
      {{ label.name }}
      <span
          role="button"
          tabindex="0"
          :aria-label="t('common.remove')"
          class="opacity-70 cursor-pointer"
          @click.stop="emit('remove', label.id)"
          @keydown.enter.space.stop.prevent="emit('remove', label.id)"
      >x</span>
    </BaseBadge>
    <BaseBadge
        v-for="name in drafts"
        :key="`draft-${name}`"
        bg-class="bg-primary/15"
        class="inline-flex items-center gap-1 border border-dashed border-(--border)"
    >
      {{ name }}
      <span class="text-xs opacity-70">{{ t('labelSelect.draft') }}</span>
      <span
          role="button"
          tabindex="0"
          :aria-label="t('common.remove')"
          class="opacity-70 cursor-pointer"
          @click.stop="emit('dropDraft', name)"
          @keydown.enter.space.stop.prevent="emit('dropDraft', name)"
      >x</span>
    </BaseBadge>
    <span v-if="selected.length === 0 && drafts.length === 0" class="text-sm text-(--text-muted)">
      {{ placeholder }}
    </span>
  </div>
</template>
