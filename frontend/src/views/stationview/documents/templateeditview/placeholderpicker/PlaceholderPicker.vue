/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, nextTick, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import SearchInput from '@/components/input/text/SearchInput.vue'
import type {Placeholder} from '@/api/generated/schema'
import {branchesAlong, offeredPlaceholders, placeholderTree, searchPlaceholders} from './placeholderTree'
import {formatsOf, placeholderLabels, usePlaceholderDates} from './placeholderDates'
import {writtenPlaceholderKey, type PlaceholderChoice} from './placeholderKey'
import PlaceholderBreadcrumb from './PlaceholderBreadcrumb.vue'
import PlaceholderCategories from './PlaceholderCategories.vue'
import PlaceholderDateFormat from './PlaceholderDateFormat.vue'
import PlaceholderLevel from './PlaceholderLevel.vue'
import PlaceholderMatches from './PlaceholderMatches.vue'

/**
 * Picks a placeholder to insert by walking its path: the categories stand in a row at the top, and
 * choosing one lists what it holds, such as the member's details and profile, the headings of the
 * profile form, or a pronoun's role and place in the sentence. The steps taken stand above the list
 * and lead back. A search looks through every step at once and lists the matches with their path.
 *
 * <p>A date takes one step more: its formats, each shown on an example day in the template's language,
 * and an own one. The date is handed on with the format in its key and the example in its label. Where
 * no editor above names the formats, a date is handed on as it is.
 *
 * <p>Picking a placeholder hands it on and starts over at the top. The steps come from the station's
 * catalogue, so nothing here reads structure from a key.
 *
 * <p>A legal template is not offered the name a member is called by. Whether the values of an
 * appointment are offered is decided with the placeholders handed in, by whether the template is for
 * appointments.
 */
const props = defineProps<{
  placeholders: Placeholder[]
  legal: boolean
}>()

const emit = defineEmits<{
  pick: [choice: PlaceholderChoice]
}>()

const {t} = useI18n()
const dates = usePlaceholderDates()

const root = ref<HTMLElement | null>(null)
const query = ref('')
const trail = ref<string[]>([])
const dating = ref<Placeholder | null>(null)
const datingFormats = computed(() => dating.value && dates ? formatsOf(dating.value, dates.value.formats) : [])
const dateLanguage = computed(() => dates?.value.language)

watch(query, () => {
  dating.value = null
})

const offered = computed(() => offeredPlaceholders(props.placeholders, {legal: props.legal}))
const categories = computed(() => placeholderTree(offered.value))
const branches = computed(() => branchesAlong(categories.value, trail.value))
const steps = computed(() => branches.value.map(branch => branch.name))
const current = computed(() => branches.value.at(-1))
const matches = computed(() => searchPlaceholders(offered.value, query.value))
const searching = computed(() => query.value.trim().length > 0)

function toggleCategory(name: string) {
  dating.value = null
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
  if (dates && formatsOf(placeholder, dates.value.formats).length > 0) dating.value = placeholder
  else hand({key: placeholder.key, label: placeholder.label})
}

function chooseFormat(format: string) {
  const placeholder = dating.value
  if (!placeholder || !dates) return
  const key = writtenPlaceholderKey(placeholder.key, format)
  hand({key, label: placeholderLabels([placeholder], dates.value).get(key) ?? placeholder.label})
}

function hand(choice: PlaceholderChoice) {
  emit('pick', choice)
  trail.value = []
  query.value = ''
  dating.value = null
}
</script>

<template>
  <div ref="root" class="space-y-2" data-testid="placeholder-picker">
    <div class="flex flex-col gap-2 sm:flex-row sm:items-start">
      <PlaceholderCategories class="flex-1" :categories="categories" :active="steps[0]" @toggle="toggleCategory"/>
      <SearchInput v-model="query" class="sm:max-w-56" data-testid="placeholder-picker-search"
                   :placeholder="t('documentTemplates.placeholderPicker.search')"/>
    </div>
    <PlaceholderDateFormat v-if="dating && dateLanguage" :placeholder="dating" :formats="datingFormats" :language="dateLanguage"
                           @choose="chooseFormat" @back="dating = null"/>
    <PlaceholderMatches v-else-if="searching" :matches="matches" @pick="pick"/>
    <template v-else-if="current">
      <PlaceholderBreadcrumb :steps="steps" @back="back"/>
      <PlaceholderLevel :nodes="current.children" @open="open" @pick="pick"/>
    </template>
  </div>
</template>
