/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import type {FieldSet} from '@/api/generated/schema'

/**
 * The group this field belongs to. A field joining a group is asked of everybody the group is asked of,
 * at the group's end.
 */
const fieldSetId = defineModel<number | null>({required: true})

defineProps<{
  sets: FieldSet[]
}>()

const {t} = useI18n()

const choice = computed({
  get: () => (fieldSetId.value != null ? String(fieldSetId.value) : ''),
  set: value => {
    fieldSetId.value = value ? Number(value) : null
  },
})
</script>

<template>
  <div v-if="sets.length > 0" class="space-y-1">
    <FieldLabel>{{ t('membersConfig.sets.field') }}</FieldLabel>
    <SelectInput v-model="choice" class="w-full" data-testid="field-set-choice">
      <option value="">{{ t('membersConfig.sets.none') }}</option>
      <option v-for="set in sets" :key="set.id" :value="String(set.id)">{{ set.name }}</option>
    </SelectInput>
    <MutedText size="xs" tag="p">{{ t('membersConfig.sets.fieldHint') }}</MutedText>
  </div>
</template>
