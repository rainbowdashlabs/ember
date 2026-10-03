/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import type {DocumentLanguage} from '@/api/generated/schema'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import LabelledField from '@/components/input/LabelledField.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {compileDateFormat, DATE_FORMAT_MAX_LENGTH} from '@/util/dateFormatPattern'
import {exampleOf} from './placeholderDates'

/**
 * An own date format, written in tokens and shown on the example day while it is typed. It is inserted
 * only once it can be printed: every letter a token, at most as long as the server takes, and a time of
 * day only for a date that has one. Until something is typed, the line below names the tokens.
 */
const props = defineProps<{
  /** Whether the date has a time of day, which is what lets the format print hours and minutes. */
  clock: boolean
  language: DocumentLanguage
}>()

const emit = defineEmits<{
  choose: [format: string]
}>()

const {t} = useI18n()

const pattern = ref('')
const written = computed(() => pattern.value.trim())
const compiled = computed(() => compileDateFormat(written.value))

const problem = computed(() => {
  const read = compiled.value
  if (!read.ok) {
    if (read.problem === 'empty') return null
    return t(`documentTemplates.dateFormat.problem.${read.problem}`, {detail: read.detail ?? '', max: DATE_FORMAT_MAX_LENGTH})
  }
  return read.clock && !props.clock ? t('documentTemplates.dateFormat.problem.clock') : null
})

const preview = computed(() => problem.value === null ? exampleOf(compiled.value, props.language) : null)

function insert() {
  if (preview.value !== null) emit('choose', written.value)
}
</script>

<template>
  <LabelledField :label="t('documentTemplates.dateFormat.own')" data-testid="placeholder-own-date-format">
    <div class="flex items-center gap-2">
      <TextInput v-model="pattern" class="flex-1" :placeholder="t('documentTemplates.dateFormat.ownPrompt')"
                 @keydown.enter.prevent="insert"/>
      <SecondaryButton compact :icon="['fas', 'plus']" :disabled="preview === null" data-testid="placeholder-own-date-insert"
                       @click="insert">
        {{ t('documentTemplates.dateFormat.insert') }}
      </SecondaryButton>
    </div>
    <p v-if="problem" class="text-xs text-error" role="alert">{{ problem }}</p>
    <MutedText v-else-if="preview" tag="p" data-testid="placeholder-own-date-preview">
      {{ t('documentTemplates.dateFormat.preview', {example: preview}) }}
    </MutedText>
    <MutedText v-else tag="p">{{ t('documentTemplates.dateFormat.tokens') }}</MutedText>
  </LabelledField>
</template>
