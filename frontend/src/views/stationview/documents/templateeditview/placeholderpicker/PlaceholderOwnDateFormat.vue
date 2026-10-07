/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, shallowRef} from 'vue'
import {watchDebounced} from '@vueuse/core'
import {useI18n} from 'vue-i18n'
import {DateFormatProblem, type DateFormatCheck} from '@/api/generated/schema'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import LabelledField from '@/components/input/LabelledField.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import MutedText from '@/components/typography/MutedText.vue'

/**
 * An own date format, written in tokens and shown on the example day once typing pauses. The server
 * reads it, and it is inserted only once the server can print it: every letter a token, at most as long
 * as the server takes, and a time of day only for a date that has one. Until something is typed, the
 * line below names the tokens.
 */
const props = defineProps<{
  /** Whether the date has a time of day, which is what lets the format print hours and minutes. */
  clock: boolean
  /** Asks the server for the example day in the format, or why it cannot be printed. */
  check: (pattern: string, clock: boolean) => Promise<DateFormatCheck>
}>()

const emit = defineEmits<{
  choose: [format: string]
}>()

const {t} = useI18n()

const TYPING_PAUSE_MS = 300

/** A format and what the server said about it. */
interface CheckedFormat {
  pattern: string
  answer: DateFormatCheck
}

const pattern = ref('')
const written = computed(() => pattern.value.trim())
const checked = shallowRef<CheckedFormat | null>(null)

watchDebounced(written, async typed => {
  const answer = typed ? await props.check(typed, props.clock).catch(() => null) : null
  if (written.value === typed) checked.value = answer ? {pattern: typed, answer} : null
}, {debounce: TYPING_PAUSE_MS})

const answer = computed(() => checked.value?.pattern === written.value ? checked.value.answer : null)

const problem = computed(() => {
  const refusal = answer.value?.problem
  if (!refusal || refusal === DateFormatProblem.EMPTY) return null
  return t(`documentTemplates.dateFormat.problem.${refusal}`, {detail: answer.value?.detail ?? ''})
})

const preview = computed(() => answer.value?.example ?? null)

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
        {{ t('common.insert') }}
      </SecondaryButton>
    </div>
    <p v-if="problem" class="text-xs text-error" role="alert">{{ problem }}</p>
    <MutedText v-else-if="preview" tag="p" data-testid="placeholder-own-date-preview">
      {{ t('documentTemplates.dateFormat.preview', {example: preview}) }}
    </MutedText>
    <MutedText v-else tag="p">{{ t('documentTemplates.dateFormat.tokens') }}</MutedText>
  </LabelledField>
</template>
