/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import type {AcceptableValue} from 'reka-ui'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DropdownPanel from './dropdown/DropdownPanel.vue'
import DropdownListbox from './dropdown/DropdownListbox.vue'
import DropdownGroups from './dropdown/DropdownGroups.vue'
import DropdownOption from './dropdown/DropdownOption.vue'
import DropdownSearch from './dropdown/DropdownSearch.vue'
import {filterOptions, groupOptions, type SelectOption} from './dropdown/groupOptions'

/**
 * Any number of choices out of a list, behind a button that names the first two and counts the
 * rest. The panel stays open while the reader ticks through it, and offers all and none at once.
 */
const modelValue = defineModel<string[]>({required: true})

const props = defineProps<{
  options: SelectOption[]
  placeholder?: string
  disabled?: boolean
  searchable?: boolean
  /** Lands on the button that opens the list, so a label outside can name it. */
  id?: string
}>()

const {t} = useI18n()

const open = ref(false)
const searchQuery = ref('')

const selectedSet = computed(() => new Set(modelValue.value))

const groupedOptions = computed(() => groupOptions(props.searchable ? filterOptions(props.options, searchQuery.value) : props.options))

const triggerLabel = computed(() => {
  if (modelValue.value.length === 0) return props.placeholder ?? t('dropdown.choose')
  const selectedLabels = props.options.filter(o => selectedSet.value.has(o.value)).map(o => o.label)
  if (selectedLabels.length <= 2) return selectedLabels.join(', ')
  return `${selectedLabels.slice(0, 2).join(', ')} +${selectedLabels.length - 2}`
})

const allSelected = computed(() => props.options.length > 0 && modelValue.value.length === props.options.length)

watch(open, isOpen => {
  if (!isOpen) searchQuery.value = ''
})

function choose(values: AcceptableValue | AcceptableValue[] | undefined) {
  modelValue.value = Array.isArray(values) ? values.map(String) : []
}

function selectAll() {
  modelValue.value = props.options.map(o => o.value)
}

function selectNone() {
  modelValue.value = []
}
</script>

<template>
  <div class="inline-block">
    <DropdownPanel v-model:open="open">
      <template #trigger>
        <SecondaryButton :id="id" :disabled="disabled" aria-haspopup="listbox">
          {{ triggerLabel }}
          <font-awesome-icon
              :icon="['fas', 'chevron-down']"
              :class="['ml-1.5 h-3 w-3 transition-transform duration-150', open ? 'rotate-180' : '']"
          />
        </SecondaryButton>
      </template>
      <DropdownListbox :model-value="modelValue" multiple :label="placeholder ?? t('dropdown.choose')" @update:model-value="choose">
        <template #head>
          <div class="flex gap-2 border-b border-(--border) px-3 py-2 text-xs">
            <button type="button" class="cursor-pointer text-primary hover:underline" :class="{'opacity-50': allSelected}"
                    :disabled="allSelected" @click="selectAll">
              {{ t('dropdown.selectAll') }}
            </button>
            <span class="text-(--text)">/</span>
            <button type="button" class="cursor-pointer text-primary hover:underline" :class="{'opacity-50': modelValue.length === 0}"
                    :disabled="modelValue.length === 0" @click="selectNone">
              {{ t('dropdown.selectNone') }}
            </button>
          </div>
          <DropdownSearch v-if="searchable" v-model="searchQuery" :placeholder="t('dropdown.search')"/>
        </template>
        <DropdownGroups v-slot="{option}" :groups="groupedOptions">
          <DropdownOption :value="option.value">
            <font-awesome-icon
                :icon="['fas', selectedSet.has(option.value) ? 'square-check' : 'square']"
                :class="selectedSet.has(option.value) ? 'text-primary' : 'text-(--text) opacity-40'"
                class="h-4 w-4"
            />
            <span>{{ option.label }}</span>
          </DropdownOption>
        </DropdownGroups>
      </DropdownListbox>
    </DropdownPanel>
  </div>
</template>
