/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import type {AcceptableValue} from 'reka-ui'
import MutedText from '@/components/typography/MutedText.vue'
import DropdownPanel from './dropdown/DropdownPanel.vue'
import DropdownListbox from './dropdown/DropdownListbox.vue'
import DropdownOption from './dropdown/DropdownOption.vue'
import DropdownSearch from './dropdown/DropdownSearch.vue'

/**
 * One choice out of a long list, found by typing. The field reads the current choice, or the value
 * itself where it is not among the options, and the search matches labels and values alike.
 */
const model = defineModel<string>()

const props = defineProps<{
  options: { value: string; label: string }[]
  placeholder?: string
  disabled?: boolean
}>()

const {t} = useI18n()

const search = ref('')
const open = ref(false)

const filteredOptions = computed(() => {
  const q = search.value.toLowerCase()
  if (!q) return props.options
  return props.options.filter(o => o.label.toLowerCase().includes(q) || o.value.toLowerCase().includes(q))
})

const selectedLabel = computed(() => props.options.find(o => o.value === model.value)?.label ?? model.value ?? '')

watch(open, isOpen => {
  if (!isOpen) search.value = ''
})

function select(value: AcceptableValue | AcceptableValue[] | undefined) {
  model.value = String(value ?? '')
  open.value = false
}
</script>

<template>
  <div>
    <DropdownPanel v-model:open="open" match-width panel-class="max-h-60">
      <template #trigger>
        <button
            :disabled="disabled"
            aria-haspopup="listbox"
            class="w-full px-3 py-2 rounded-theme border border-bg-light-accent bg-bg-light text-(--text) text-left transition-colors duration-150 outline-none focus:border-primary focus:ring-1 focus:ring-primary disabled:opacity-50 disabled:cursor-not-allowed dark:border-bg-dark-accent dark:bg-bg-dark"
            type="button"
        >
          {{ selectedLabel || placeholder || '' }}
        </button>
      </template>
      <DropdownListbox :model-value="model" :label="placeholder" @update:model-value="select">
        <template #head>
          <DropdownSearch v-model="search" :placeholder="placeholder ?? ''"/>
        </template>
        <DropdownOption
            v-for="opt in filteredOptions"
            :key="opt.value"
            :value="opt.value"
            class="data-[state=checked]:font-medium"
        >
          {{ opt.label }}
        </DropdownOption>
        <MutedText v-if="filteredOptions.length === 0" tag="div" size="sm" class="py-2 px-3">
          {{ t('dropdown.noResults') }}
        </MutedText>
      </DropdownListbox>
    </DropdownPanel>
  </div>
</template>
