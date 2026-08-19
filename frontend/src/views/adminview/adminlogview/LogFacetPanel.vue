/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {LogFacet} from '@/api/applicationLog'

/**
 * One list of values the log can be narrowed to, with what each is worth.
 *
 * The list is cut off at what fits, so it carries its own search: a logger quiet enough to fall
 * below the busiest twenty is otherwise unreachable, which is the case where narrowing would have
 * helped most.
 */
const props = defineProps<{
  label: string
  facets: LogFacet[]
  selected: string
  /** Searches beyond what the page carried. Returns the matches to show instead. */
  search: (name: string) => Promise<LogFacet[]>
}>()

const emit = defineEmits<{
  (e: 'select', value: string): void
}>()

const {t} = useI18n()

const term = ref('')
const found = ref<LogFacet[] | null>(null)

watch(term, async value => {
  if (!value.trim()) {
    found.value = null
    return
  }
  found.value = await props.search(value.trim())
})

/** A chosen value stays on the list even when a search would not have found it. */
function shown(): LogFacet[] {
  const list = found.value ?? props.facets
  if (!props.selected || list.some(f => f.value === props.selected)) return list
  return [{value: props.selected, count: 0}, ...list]
}
</script>

<template>
  <div class="space-y-2">
    <FieldLabel>{{ label }}</FieldLabel>
    <TextInput v-model="term" :aria-label="label" :placeholder="t('applicationLog.facetSearch')"/>
    <MutedText v-if="shown().length === 0" size="sm" tag="p">{{ t('applicationLog.facetEmpty') }}</MutedText>
    <div class="max-h-56 overflow-y-auto space-y-1">
      <button
          v-for="facet in shown()"
          :key="facet.value"
          :class="facet.value === selected ? 'bg-(--bg-accent) font-semibold' : 'hover:bg-(--bg-accent)'"
          class="flex w-full items-baseline justify-between gap-2 rounded px-2 py-1 text-left text-xs"
          type="button"
          @click="emit('select', facet.value === selected ? '' : facet.value)"
      >
        <span class="truncate font-mono" :title="facet.value">{{ facet.value }}</span>
        <span class="shrink-0 text-(--text-muted)">{{ facet.count }}</span>
      </button>
    </div>
  </div>
</template>
