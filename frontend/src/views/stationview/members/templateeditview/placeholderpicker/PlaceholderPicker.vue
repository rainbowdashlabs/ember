/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, nextTick, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import SearchInput from '@/components/input/text/SearchInput.vue'
import type {Placeholder} from '@/api/generated/schema'
import {branchesAlong, offeredPlaceholders, placeholderTree, searchPlaceholders} from './placeholderTree'
import PlaceholderBreadcrumb from './PlaceholderBreadcrumb.vue'
import PlaceholderCategories from './PlaceholderCategories.vue'
import PlaceholderLevel from './PlaceholderLevel.vue'
import PlaceholderMatches from './PlaceholderMatches.vue'

/**
 * Picks a placeholder to insert by walking its path: the categories stand in a row at the top, and
 * choosing one lists what it holds, such as the member's details and profile, the headings of the
 * profile form, or a pronoun's role and place in the sentence. The steps taken stand above the list
 * and lead back. A search looks through every step at once and lists the matches with their path.
 *
 * <p>Picking a placeholder hands it on and starts over at the top. The steps come from the station's
 * catalogue, so nothing here reads structure from a key.
 *
 * <p>A legal template is not offered the name a member is called by, and nothing that only an
 * appointment fills is offered while documents are generated for members alone. A signature field is
 * offered only where it can stand, which is the body of a letter.
 */
const props = defineProps<{
  placeholders: Placeholder[]
  legal: boolean
  /** Whether signature fields are offered too. */
  signatures?: boolean
  /** Whether the values of the appointment a document is generated for are offered too. */
  appointments?: boolean
}>()

const emit = defineEmits<{
  pick: [placeholder: Placeholder]
}>()

const {t} = useI18n()

const root = ref<HTMLElement | null>(null)
const query = ref('')
const trail = ref<string[]>([])

const offered = computed(() => offeredPlaceholders(props.placeholders, {
  legal: props.legal,
  signatures: props.signatures ?? false,
  appointments: props.appointments ?? false,
}))
const categories = computed(() => placeholderTree(offered.value))
const branches = computed(() => branchesAlong(categories.value, trail.value))
const steps = computed(() => branches.value.map(branch => branch.name))
const current = computed(() => branches.value.at(-1))
const matches = computed(() => searchPlaceholders(offered.value, query.value))
const searching = computed(() => query.value.trim().length > 0)

function toggleCategory(name: string) {
  trail.value = steps.value[0] === name ? [] : [name]
}

/** Puts the focus on the first entry of the step just reached, as the entry that held it is gone. */
async function walk(to: string[]) {
  trail.value = to
  await nextTick()
  root.value?.querySelector<HTMLElement>('[data-testid="placeholder-picker-level"] button')?.focus()
}

function open(name: string) {
  void walk([...steps.value, name])
}

function back(depth: number) {
  void walk(steps.value.slice(0, depth + 1))
}

function pick(placeholder: Placeholder) {
  emit('pick', placeholder)
  trail.value = []
  query.value = ''
}
</script>

<template>
  <div ref="root" class="space-y-2" data-testid="placeholder-picker">
    <div class="flex flex-col gap-2 sm:flex-row sm:items-start">
      <PlaceholderCategories class="flex-1" :categories="categories" :active="steps[0]" @toggle="toggleCategory"/>
      <SearchInput v-model="query" class="sm:max-w-56" data-testid="placeholder-picker-search"
                   :placeholder="t('documentTemplates.placeholderPicker.search')"/>
    </div>
    <PlaceholderMatches v-if="searching" :matches="matches" @pick="pick"/>
    <template v-else-if="current">
      <PlaceholderBreadcrumb :steps="steps" @back="back"/>
      <PlaceholderLevel :nodes="current.children" @open="open" @pick="pick"/>
    </template>
  </div>
</template>
