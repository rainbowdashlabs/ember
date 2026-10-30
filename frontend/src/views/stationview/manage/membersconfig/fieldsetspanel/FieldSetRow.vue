/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {FieldSet, FieldSetRequest} from '@/api/generated/schema'

/**
 * One group of fields: its name, written back when the box is left, whether the name stands above its
 * fields, how many fields it holds, and dissolving it. Dissolving keeps the fields.
 */
const props = defineProps<{
  set: FieldSet
  fieldNames: string[]
}>()

const emit = defineEmits<{
  update: [set: FieldSet, patch: Partial<FieldSetRequest>]
  dissolve: [set: FieldSet]
}>()

const {t} = useI18n()

const name = ref(props.set.name)

watch(() => props.set.name, value => {
  name.value = value
})

function saveName() {
  const chosen = name.value.trim()
  if (!chosen || chosen === props.set.name) {
    name.value = props.set.name
    return
  }
  emit('update', props.set, {name: chosen})
}
</script>

<template>
  <div class="space-y-2 border-b border-(--border) pb-3 last:border-b-0 last:pb-0" :data-testid="`field-set-${set.name}`">
    <div class="flex flex-wrap items-center gap-3">
      <TextInput v-model="name" class="min-w-0 flex-1" :aria-label="t('membersConfig.sets.name')"
                 @blur="saveName" @keydown.enter="saveName"/>
      <label class="flex items-center gap-2 text-sm">
        <ToggleInput :model-value="set.nameAsSection" :data-testid="`field-set-heading-${set.name}`"
                     @update:model-value="(value: boolean) => emit('update', set, {nameAsSection: value})"/>
        {{ t('membersConfig.sets.heading') }}
      </label>
      <DeleteButton :data-testid="`field-set-dissolve-${set.name}`" @click="emit('dissolve', set)"/>
    </div>
    <MutedText size="xs" tag="p">
      {{ fieldNames.length > 0 ? fieldNames.join(', ') : t('membersConfig.sets.empty') }}
    </MutedText>
  </div>
</template>
