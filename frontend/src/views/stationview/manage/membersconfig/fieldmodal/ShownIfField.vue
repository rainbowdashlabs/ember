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
import type {EditableField} from '@/api/profileFields'

/**
 * The yes/no question this one is only shown behind.
 *
 * <p>Offers the owner's yes/no questions except the one being edited. A member asked only this question
 * still sees it; the dependency only acts where both are on the same form.
 */
const shownIf = defineModel<number | null>({required: true})

const props = defineProps<{
  booleanFields: EditableField[]
  /** The question being edited, which cannot depend on itself. */
  fieldId: number | null
}>()

const {t} = useI18n()

const choices = computed(() => props.booleanFields.filter(field => field.id !== props.fieldId))

const choice = computed({
  get: () => (shownIf.value != null ? String(shownIf.value) : ''),
  set: value => {
    shownIf.value = value ? Number(value) : null
  },
})
</script>

<template>
  <div v-if="choices.length > 0" class="space-y-1">
    <FieldLabel>{{ t('membersConfig.shownIf') }}</FieldLabel>
    <SelectInput v-model="choice" class="w-full" data-testid="field-shown-if">
      <option value="">{{ t('membersConfig.shownAlways') }}</option>
      <option v-for="field in choices" :key="field.id" :value="String(field.id)">{{ field.name }}</option>
    </SelectInput>
    <MutedText size="xs" tag="p">{{ t('membersConfig.shownIfHint') }}</MutedText>
  </div>
</template>
