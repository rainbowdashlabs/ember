/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import SelectInput from '@/components/input/select/SelectInput.vue'
import {PlaceholderGroup, type Placeholder} from '@/api/generated/schema'

/**
 * Picks a placeholder to insert, grouped by where its value comes from.
 *
 * <p>A legal template is not offered the name a member is called by, and nothing that only an
 * appointment fills is offered while documents are generated for members alone.
 */
const props = defineProps<{
  placeholders: Placeholder[]
  legal: boolean
}>()

const emit = defineEmits<{
  pick: [placeholder: Placeholder]
}>()

const {t} = useI18n()

/** Counts the picks, so the menu is drawn anew and shows its prompt again after each one. */
const picks = ref(0)

const GROUP_ORDER: readonly PlaceholderGroup[] = [
  PlaceholderGroup.MEMBER,
  PlaceholderGroup.PRONOUN,
  PlaceholderGroup.PROFILE,
  PlaceholderGroup.GUARDIAN,
  PlaceholderGroup.STATION,
  PlaceholderGroup.DOCUMENT,
]

const groups = computed(() => GROUP_ORDER
    .map(group => ({
      group,
      entries: props.placeholders.filter(placeholder =>
          placeholder.group === group && !placeholder.eventOnly && !(props.legal && placeholder.informal)),
    }))
    .filter(entry => entry.entries.length > 0))

function pick(key: string | number | null | undefined) {
  const placeholder = props.placeholders.find(candidate => candidate.key === key)
  if (placeholder) emit('pick', placeholder)
  picks.value++
}
</script>

<template>
  <SelectInput :key="picks" :model-value="null" data-testid="placeholder-picker" :aria-label="t('documentTemplates.insertPlaceholder')"
               @update:model-value="pick">
    <option :value="null" disabled>{{ t('documentTemplates.insertPlaceholder') }}</option>
    <optgroup v-for="entry in groups" :key="entry.group" :label="t(`documentTemplates.group.${entry.group}`)">
      <option v-for="placeholder in entry.entries" :key="placeholder.key" :value="placeholder.key">
        {{ placeholder.label }}
      </option>
    </optgroup>
  </SelectInput>
</template>
