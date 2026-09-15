/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SelectInput from '@/components/input/select/SelectInput.vue'
import {type ProfileField} from '@/api/profileFields'
import {
  Writability, useFieldsCapabilities, writabilityOf, type WritabilityName,
} from '@/composables/useFieldsConfig'

/**
 * Who may change the answer to this question, as one named choice rather than as a switch.
 *
 * <p>An association can lock a question from the member, from the member and the station both, or
 * from nobody. Those are two flags underneath, and the pair has a fourth combination that says the
 * member may write an answer their own station may not, which is not a thing anybody wants. Naming
 * the three that make sense is what puts the fourth out of reach.
 *
 * <p>A station has nobody above it and so has two rungs rather than three, but it gets the same
 * named choice. It used to get a bare switch under the heading "changeable by", which asks for a
 * who and is answered by an on and an off: nothing on the screen said which way round it meant.
 *
 * <p>Both flags are the question's own, so this sits on the question rather than beside one of its
 * audiences: who may write an answer holds for everybody who is asked it.
 */
const props = defineProps<{
  field: ProfileField
}>()

const emit = defineEmits<{
  set: [field: ProfileField, level: WritabilityName]
}>()

const {t} = useI18n()

const capabilities = useFieldsCapabilities()

/**
 * The rungs this owner has. Only an association can keep a question from the station, so only an
 * association is offered the third; a station reading its own screen is never shown a choice that
 * would mean nothing there.
 */
const levels = computed<WritabilityName[]>(() => capabilities.writability
    ? [Writability.EVERYONE, Writability.NOT_MEMBER, Writability.OWNER_ONLY]
    : [Writability.EVERYONE, Writability.NOT_MEMBER])

/** A station and an association name the same rung differently, because they lock out different people. */
function label(level: WritabilityName): string {
  return capabilities.writability
      ? t(`membersConfig.writability.${level}`)
      : t(`membersConfig.writability.station.${level}`)
}
</script>

<template>
  <SelectInput
      :model-value="writabilityOf(props.field)"
      :title="t('membersConfig.writability.hint')"
      class="min-w-44"
      @update:model-value="emit('set', props.field, String($event) as WritabilityName)"
  >
    <option v-for="level in levels" :key="level" :value="level">{{ label(level) }}</option>
  </SelectInput>
</template>
