/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onBeforeUnmount, onMounted, ref} from 'vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import type {SelectOption} from '@/components/input/select/MultiSelectDropdown.vue'

const props = defineProps<{
  options: SelectOption[]
  modelValue: string
  placeholder?: string
  disabled?: boolean
  clearable?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const open = ref(false)
const containerRef = ref<HTMLElement | null>(null)

const selectedLabel = computed(() => {
  const opt = props.options.find(o => o.value === props.modelValue)
  return opt?.label ?? props.placeholder ?? 'Auswahl'
})

const hasSelection = computed(() => props.modelValue !== '' && props.options.some(o => o.value === props.modelValue))

const groupedOptions = computed(() => {
  const hasGroups = props.options.some(o => o.group)
  if (!hasGroups) return [{group: undefined as string | undefined, options: props.options}]

  const map = new Map<string | undefined, SelectOption[]>()
  for (const opt of props.options) {
    const key = opt.group
    if (!map.has(key)) map.set(key, [])
    map.get(key)!.push(opt)
  }
  return Array.from(map.entries()).map(([group, options]) => ({group, options}))
})

function select(value: string) {
  emit('update:modelValue', value)
  open.value = false
}

function clear() {
  emit('update:modelValue', '')
  open.value = false
}

function onClickOutside(e: MouseEvent) {
  if (containerRef.value && !containerRef.value.contains(e.target as Node)) {
    open.value = false
  }
}

onMounted(() => document.addEventListener('click', onClickOutside))
onBeforeUnmount(() => document.removeEventListener('click', onClickOutside))
</script>

<template>
  <div ref="containerRef" class="relative inline-block">
    <SecondaryButton :disabled="disabled" @click="open = !open">
      {{ selectedLabel }}
      <font-awesome-icon
        :icon="['fas', 'chevron-down']"
        :class="['ml-1.5 h-3 w-3 transition-transform duration-150', open ? 'rotate-180' : '']"
      />
    </SecondaryButton>

    <div
      v-if="open"
      class="absolute z-20 mt-1 w-64 max-h-72 rounded-lg border border-bg-light-accent bg-bg-light shadow-lg dark:border-bg-dark-accent dark:bg-bg-dark flex flex-col"
    >
      <!-- Clear option -->
      <button
        v-if="clearable && hasSelection"
        type="button"
        class="w-full flex items-center gap-2 px-3 py-2 text-sm text-left text-error hover:bg-error/5 border-b border-bg-light-accent dark:border-bg-dark-accent transition-colors cursor-pointer"
        @click.stop="clear"
      >
        <font-awesome-icon :icon="['fas', 'xmark']" class="h-4 w-4" />
        <span>Auswahl aufheben</span>
      </button>

      <!-- Options -->
      <div class="overflow-y-auto">
        <template v-for="(section, idx) in groupedOptions" :key="idx">
          <div
            v-if="section.group"
            class="px-3 py-1.5 text-xs font-semibold text-[var(--text)] opacity-60 uppercase tracking-wide"
          >
            {{ section.group }}
          </div>
          <button
            v-for="opt in section.options"
            :key="opt.value"
            type="button"
            :class="[
              'w-full flex items-center gap-2 px-3 py-2 text-sm text-left transition-colors cursor-pointer',
              opt.value === modelValue
                ? 'bg-primary/10 text-primary font-medium'
                : 'hover:bg-primary/5'
            ]"
            @click.stop="select(opt.value)"
          >
            <font-awesome-icon
              v-if="opt.value === modelValue"
              :icon="['fas', 'check']"
              class="h-3 w-3 text-primary"
            />
            <span :class="opt.value !== modelValue ? 'ml-5' : ''">{{ opt.label }}</span>
          </button>
        </template>
      </div>
    </div>
  </div>
</template>
