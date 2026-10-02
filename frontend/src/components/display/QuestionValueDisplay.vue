/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {FieldTypes} from '@/api/fieldTypes'
import {parseFieldConfig, type FieldSettings} from '@/api/profileFields'
import {computeAge} from '@/util/age'
import {answerText, isYes} from '@/util/questions'
import ExpiryStateBadge from './ExpiryStateBadge.vue'

/**
 * An answer as a station reads it, whatever feature asked the question.
 *
 * <p>Five screens drew answers their own way and three printed what was stored: a yes was an icon on
 * one, a badge on another and the word {@code true} on a third, and a yes stored as {@code 1} showed as
 * no where only {@code true} was known. Here every type is drawn one way: a yes and a no with a mark
 * and a word, a day as the day it is, a time as a clock reads, and members by their names.
 */
const props = defineProps<{
  value: unknown
  fieldType?: string | null
  /** The field's own settings, which say whether a birth date carries its age behind it. */
  config?: FieldSettings | null
  /**
   * Writes a date as the day it is, without what today makes of it: no age behind a birth date and
   * no state beside an expiry date. For a value from the past, which today says nothing about.
   */
  bare?: boolean
  /** The station's members by id, for the types that name people. */
  memberNames?: Map<number, string>
  /** What a yes is called, where the field calls it something of its own. */
  yesLabel?: string
  /** What a no is called, where the field calls it something of its own. */
  noLabel?: string
}>()

const {t} = useI18n()

const empty = computed(() => props.value == null || props.value === '')

const isBoolean = computed(() => props.fieldType === FieldTypes.BOOLEAN)
const yes = computed(() => isYes(props.value))
const booleanWord = computed(() =>
    yes.value ? (props.yesLabel || t('common.yes')) : (props.noLabel || t('common.no')))

/**
 * Whether a birth date is written with the age behind it.
 *
 * <p>On unless the field says otherwise, because every birth date carried it before there was a
 * choice. A station that also asks the age as a question of its own switches it off here, so one
 * screen does not state an age twice and risk stating it two ways.
 */
const showsAge = computed(() => !props.bare && parseFieldConfig(props.config).showAge !== false)

const showsExpiry = computed(() => !props.bare && props.fieldType === FieldTypes.EXPIRY_DATE && !empty.value)

/**
 * The answer written out. A date nobody can parse is left as written, because showing nothing at
 * all would lose it. A birth date carries the current age behind it, which is the number the reader
 * is usually after.
 */
const text = computed(() => {
  const written = answerText(props.fieldType, props.value, {
    yes: t('common.yes'),
    no: t('common.no'),
    names: props.memberNames,
  })
  if (props.fieldType !== FieldTypes.BIRTH_DATE || !showsAge.value) return written
  const age = computeAge(String(props.value), 'now')
  return age ? `${written} (${age})` : written
})
</script>

<template>
  <template v-if="empty">–</template>
  <span v-else-if="isBoolean" class="inline-flex items-center gap-1" data-testid="answer-yes-no" :data-yes="yes">
    <font-awesome-icon
        :icon="['fas', yes ? 'check' : 'xmark']"
        :class="yes ? 'text-success' : 'text-error'"
        class="h-3.5 w-3.5"
    />
    {{ booleanWord }}
  </span>
  <template v-else>
    {{ text }}
    <ExpiryStateBadge v-if="showsExpiry" class="ml-1" :value="value" :config="config"/>
  </template>
</template>
