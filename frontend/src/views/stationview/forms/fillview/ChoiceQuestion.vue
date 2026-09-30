/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import type { ChoiceAnswer } from '@/api/forms'
import { optionsOf } from '@/util/formOptions'

const props = defineProps<{
  config: Record<string, unknown>
}>()

const answer = defineModel<ChoiceAnswer>({ required: true })

const { t } = useI18n()

const options = computed(() => optionsOf(props.config))
const isMulti = computed(() => !!props.config.multiSelect)
const isDropdown = computed(() => !!props.config.dropdown)
const allowOther = computed(() => !!props.config.allowOther)

const selectionCount = computed(() => answer.value.selected.length + (answer.value.other ? 1 : 0))

const atLimit = computed(() => {
  const limitType = props.config.multiLimitType as string | undefined
  const limit = props.config.multiLimit as number | undefined
  if (!limit || (limitType !== 'AT_MOST' && limitType !== 'EXACTLY')) return false
  return selectionCount.value >= limit
})

function toggle(key: string) {
  if (isMulti.value) {
    const idx = answer.value.selected.indexOf(key)
    if (idx >= 0) {
      answer.value.selected.splice(idx, 1)
    } else {
      if (atLimit.value) return
      answer.value.selected.push(key)
    }
  } else {
    answer.value.selected = [key]
    answer.value.other = ''
  }
}

/** The dropdown's empty entry clears the choice rather than picking anything. */
function pick(value: string | number | null | undefined) {
  const key = String(value ?? '')
  if (key) toggle(key)
  else answer.value.selected = []
}

function onOther(value: string | undefined) {
  const v = value ?? ''
  if (!v) {
    answer.value.other = ''
    return
  }
  if (!answer.value.other && atLimit.value) return
  answer.value.other = v
}

function selectedIcon(selected: boolean): string {
  if (selected) return isMulti.value ? 'square-check' : 'circle-dot'
  return isMulti.value ? 'square' : 'circle'
}
</script>

<template>
  <div class="space-y-1">
    <template v-if="isDropdown">
      <SelectInput
          :model-value="answer.selected?.[0] ?? ''"
          @update:model-value="pick">
        <option value="">--</option>
        <option v-for="opt in options" :key="opt.key" :value="opt.key">{{ opt.label }}</option>
      </SelectInput>
    </template>
    <div v-else role="listbox" :aria-multiselectable="isMulti" class="space-y-1">
      <div
          v-for="opt in options"
          :key="opt.key"
          role="option"
          tabindex="0"
          :aria-selected="!!answer.selected?.includes(opt.key)"
          data-testid="choice-option"
          class="flex items-center gap-2 px-4 py-3 rounded-lg border-2 text-sm font-medium transition-all cursor-pointer"
          :class="answer.selected?.includes(opt.key)
            ? 'border-primary bg-primary/10 text-primary'
            : 'border-bg-light-accent dark:border-bg-dark-accent text-(--text) hover:border-primary/50'"
          @click="toggle(opt.key)"
          @keydown.enter.prevent="toggle(opt.key)"
          @keydown.space.prevent="toggle(opt.key)"
      >
        <font-awesome-icon
            :icon="['fas', selectedIcon(answer.selected?.includes(opt.key))]"
            :class="answer.selected?.includes(opt.key) ? 'text-primary' : 'text-(--text-muted)'"
            class="shrink-0"
        />
        <span>{{ opt.label }}</span>
      </div>
    </div>
    <div v-if="allowOther"
         class="w-full flex items-center gap-2 px-4 py-3 rounded-lg border-2 transition-all"
         :class="answer.other
           ? 'border-primary bg-primary/10'
           : 'border-bg-light-accent dark:border-bg-dark-accent'"
    >
      <font-awesome-icon
          :icon="['fas', selectedIcon(!!answer.other)]"
          :class="answer.other ? 'text-primary' : 'text-(--text-muted)'"
          class="shrink-0"
      />
      <TextInput
          :model-value="answer.other"
          :disabled="!answer.other && atLimit"
          :placeholder="t('forms.otherPlaceholder')"
          class="flex-1"
          @update:model-value="onOther"
      />
    </div>
  </div>
</template>
