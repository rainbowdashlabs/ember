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
 * One choice out of a list, behind a button that reads the current one. Choosing closes it; a
 * `clearable` one also offers to take the choice back, and a `searchable` one narrows the list as
 * the reader types.
 */
const modelValue = defineModel<string>({required: true})

const props = defineProps<{
  options: SelectOption[]
  placeholder?: string
  disabled?: boolean
  clearable?: boolean
  searchable?: boolean
}>()

const {t} = useI18n()

const open = ref(false)
const searchQuery = ref('')

const groupedOptions = computed(() => groupOptions(props.searchable ? filterOptions(props.options, searchQuery.value) : props.options))

const selectedLabel = computed(() =>
    props.options.find(o => o.value === modelValue.value)?.label ?? props.placeholder ?? t('dropdown.choose'))

const hasSelection = computed(() => modelValue.value !== '' && props.options.some(o => o.value === modelValue.value))

watch(open, isOpen => {
  if (!isOpen) searchQuery.value = ''
})

function select(value: AcceptableValue | AcceptableValue[] | undefined) {
  modelValue.value = String(value ?? '')
  open.value = false
}
</script>

<template>
  <div class="inline-block">
    <DropdownPanel v-model:open="open">
      <template #trigger>
        <SecondaryButton :disabled="disabled" aria-haspopup="listbox">
          {{ selectedLabel }}
          <font-awesome-icon
              :icon="['fas', 'chevron-down']"
              :class="['ml-1.5 h-3 w-3 transition-transform duration-150', open ? 'rotate-180' : '']"
          />
        </SecondaryButton>
      </template>
      <DropdownListbox :model-value="modelValue" :label="placeholder ?? t('dropdown.choose')" @update:model-value="select">
        <template #head>
          <button
              v-if="clearable && hasSelection"
              type="button"
              class="flex w-full cursor-pointer items-center gap-2 border-b border-(--border) px-3 py-2 text-left text-sm text-error transition-colors hover:bg-error/5"
              @click="select('')"
          >
            <font-awesome-icon :icon="['fas', 'xmark']" class="h-4 w-4"/>
            <span>{{ t('dropdown.clear') }}</span>
          </button>
          <DropdownSearch v-if="searchable" v-model="searchQuery" :placeholder="t('dropdown.search')"/>
        </template>
        <DropdownGroups v-slot="{option}" :groups="groupedOptions">
          <DropdownOption :value="option.value" class="data-[state=checked]:font-medium data-[state=checked]:text-primary">
            <font-awesome-icon v-if="option.value === modelValue" :icon="['fas', 'check']" class="h-3 w-3 text-primary"/>
            <span :class="option.value !== modelValue ? 'ml-5' : ''">{{ option.label }}</span>
          </DropdownOption>
        </DropdownGroups>
      </DropdownListbox>
    </DropdownPanel>
  </div>
</template>
