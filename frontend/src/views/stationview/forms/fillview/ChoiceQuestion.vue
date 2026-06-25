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

type ChoiceAnswer = { selected: number[]; other: string }

const props = defineProps<{
  config: Record<string, unknown>
}>()

const answer = defineModel<ChoiceAnswer>({ required: true })

const { t } = useI18n()

const options = computed(() => (props.config.options as string[]) || [])
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

function toggle(optionIndex: number) {
  if (isMulti.value) {
    const idx = answer.value.selected.indexOf(optionIndex)
    if (idx >= 0) {
      answer.value.selected.splice(idx, 1)
    } else {
      if (atLimit.value) return
      answer.value.selected.push(optionIndex)
    }
  } else {
    answer.value.selected = [optionIndex]
    answer.value.other = ''
  }
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
          :model-value="String(answer.selected?.[0] ?? '')"
          @update:model-value="(v: string | undefined) => toggle(Number(v))">
        <option value="">--</option>
        <option v-for="(opt, oi) in options" :key="oi" :value="oi">{{ opt }}</option>
      </SelectInput>
    </template>
    <template v-else>
      <div
          v-for="(opt, oi) in options"
          :key="oi"
          class="flex items-center gap-2 px-4 py-3 rounded-lg border-2 text-sm font-medium transition-all cursor-pointer"
          :class="answer.selected?.includes(oi)
            ? 'border-primary bg-primary/10 text-primary'
            : 'border-bg-light-accent dark:border-bg-dark-accent text-(--text) hover:border-primary/50'"
          @click="toggle(oi)"
      >
        <font-awesome-icon
            :icon="['fas', selectedIcon(answer.selected?.includes(oi))]"
            :class="answer.selected?.includes(oi) ? 'text-primary' : 'text-(--text-muted)'"
            class="shrink-0"
        />
        <span>{{ opt }}</span>
      </div>
    </template>
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
