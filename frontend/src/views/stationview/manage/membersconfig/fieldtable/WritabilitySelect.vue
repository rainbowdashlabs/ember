/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {type ProfileField} from '@/api/profileFields'
import {Writability, writabilityOf, type WritabilityName} from '@/composables/useFieldsConfig'

/**
 * Who may change the answer to this question, as one choice rather than as two switches.
 *
 * <p>An association can lock a question from the member, from the member and the station both, or from
 * nobody. Those are two flags underneath, and the pair has a fourth combination that says the member
 * may write an answer their own station may not, which is not a thing anybody wants. Naming the three
 * that make sense is what puts the fourth out of reach.
 */
const props = defineProps<{
  field: ProfileField
}>()

const emit = defineEmits<{
  set: [field: ProfileField, level: WritabilityName]
}>()

const {t} = useI18n()

const levels: WritabilityName[] = [Writability.EVERYONE, Writability.NOT_MEMBER, Writability.OWNER_ONLY]
</script>

<template>
  <select
      :value="writabilityOf(props.field)"
      :title="t('membersConfig.writability.hint')"
      class="w-full rounded-theme border border-(--border) bg-(--bg) px-1 py-0.5 text-xs"
      @change="emit('set', props.field, ($event.target as HTMLSelectElement).value as WritabilityName)"
  >
    <option v-for="level in levels" :key="level" :value="level">
      {{ t(`membersConfig.writability.${level}`) }}
    </option>
  </select>
</template>
