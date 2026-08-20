/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import IconButton from '@/components/button/IconButton.vue'
import DropdownMenuItem from '@/components/button/DropdownMenuItem.vue'

/**
 * Free-text labels, with what has been written before offered while typing.
 *
 * <p>A label exists because somebody typed it, so the suggestions are a convenience rather than a
 * list to pick from: anything not in them is still accepted and becomes one of them.
 */
const model = defineModel<string[]>({required: true})

const props = defineProps<{
  /** Everything written so far, offered while typing. */
  suggestions?: string[]
  placeholder?: string
  disabled?: boolean
}>()

const {t} = useI18n()

const input = ref('')
const showSuggestions = ref(false)

const matching = computed(() => {
  const term = input.value.trim().toLowerCase()
  if (!term) return []
  const taken = new Set(model.value.map(tag => tag.toLowerCase()))
  return (props.suggestions ?? [])
      .filter(tag => tag.toLowerCase().includes(term) && !taken.has(tag.toLowerCase()))
      .slice(0, 8)
})

function add(name?: string) {
  const tag = (name ?? input.value).trim()
  if (!tag || model.value.some(existing => existing.toLowerCase() === tag.toLowerCase())) {
    input.value = ''
    return
  }
  model.value = [...model.value, tag]
  input.value = ''
  showSuggestions.value = false
}

function remove(tag: string) {
  model.value = model.value.filter(existing => existing !== tag)
}

/** Late enough for a click on a suggestion to land before the list is taken away. */
function hideSoon() {
  setTimeout(() => {
    showSuggestions.value = false
  }, 200)
}
</script>

<template>
  <div class="space-y-2">
    <div v-if="model.length > 0" class="flex flex-wrap gap-1">
      <span
          v-for="tag in model"
          :key="tag"
          class="inline-flex items-center gap-1 rounded-full border border-primary/20 bg-primary/10 px-2.5 py-1 text-xs font-medium text-primary"
      >
        {{ tag }}
        <IconButton
            v-if="!props.disabled"
            :icon="['fas', 'xmark']"
            :label="t('common.remove')"
            class="!p-0 text-[10px] hover:text-error"
            @click="remove(tag)"
        />
      </span>
    </div>
    <div v-if="!props.disabled" class="relative">
      <form @submit.prevent="add()">
        <TextInput
            v-model="input"
            :placeholder="props.placeholder"
            @focus="showSuggestions = true"
            @blur="hideSoon"
        />
      </form>
      <div
          v-if="showSuggestions && matching.length > 0"
          class="absolute left-0 top-full z-20 mt-1 min-w-40 rounded-theme border border-(--border) bg-(--bg) py-1 shadow-lg"
      >
        <DropdownMenuItem
            v-for="suggestion in matching"
            :key="suggestion"
            :icon="['fas', 'tag']"
            class="!py-1 !text-xs"
            @click="add(suggestion)"
        >
          {{ suggestion }}
        </DropdownMenuItem>
      </div>
    </div>
  </div>
</template>
