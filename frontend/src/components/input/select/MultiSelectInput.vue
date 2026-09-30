/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PillBadge from '@/components/badge/PillBadge.vue'
import DropdownPanel from './dropdown/DropdownPanel.vue'
import DropdownListbox from './dropdown/DropdownListbox.vue'
import DropdownOption from './dropdown/DropdownOption.vue'

/**
 * Several choices shown as chips, with a button that adds one more from whatever is not chosen yet.
 * The button goes away once everything is.
 */
const modelValue = defineModel<string[]>({required: true})

const props = defineProps<{
  options: { value: string; label: string }[]
  placeholder?: string
}>()

const {t} = useI18n()

const open = ref(false)

const selectedSet = computed(() => new Set(modelValue.value))

const availableOptions = computed(() => props.options.filter(o => !selectedSet.value.has(o.value)))

/** Adds the entry to the choices; the list itself keeps no choice of its own. */
function add(event: Event, value: string) {
  event.preventDefault()
  modelValue.value = [...modelValue.value, value]
  open.value = false
}

function remove(value: string) {
  modelValue.value = modelValue.value.filter(v => v !== value)
}

function getLabel(value: string): string {
  return props.options.find(o => o.value === value)?.label ?? value
}
</script>

<template>
  <div class="space-y-2">
    <div v-if="modelValue.length > 0" class="flex flex-wrap gap-1">
      <PillBadge
          v-for="val in modelValue"
          :key="val"
          class="gap-1 px-2.5 py-1 text-xs font-medium bg-primary/10 text-primary border border-primary/20"
      >
        {{ getLabel(val) }}
        <button class="hover:text-error" type="button" :aria-label="t('common.remove')" @click="remove(val)">
          <font-awesome-icon :icon="['fas', 'xmark']" class="h-3 w-3"/>
        </button>
      </PillBadge>
    </div>

    <DropdownPanel v-if="availableOptions.length > 0" v-model:open="open" panel-class="w-64 max-h-48">
      <template #trigger>
        <SecondaryButton :icon="['fas', 'plus']" aria-haspopup="listbox">
          {{ placeholder ?? t('dropdown.add') }}
        </SecondaryButton>
      </template>
      <DropdownListbox :label="placeholder ?? t('dropdown.add')">
        <DropdownOption
            v-for="opt in availableOptions"
            :key="opt.value"
            :value="opt.value"
            @select="add($event, opt.value)"
        >
          {{ opt.label }}
        </DropdownOption>
      </DropdownListbox>
    </DropdownPanel>
  </div>
</template>
