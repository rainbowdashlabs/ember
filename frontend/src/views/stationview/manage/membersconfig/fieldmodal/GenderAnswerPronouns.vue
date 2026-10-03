/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import LabelledField from '@/components/input/LabelledField.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import type {PronounSet} from '@/api/generated/schema'
import {
  choiceOf, pronounsFor, PRONOUN_LANGUAGES,
  type AnswerPronouns, type PronounChoice, type PronounRole,
} from './genderPronouns'

/**
 * What one answer of a gender field stands for in a document: "er" or "sie" with their forms, the
 * member's first name, or pronouns of its own per language. A language left without words uses the name.
 */
const pronouns = defineModel<AnswerPronouns | undefined>({required: true})

defineProps<{
  answer: string
}>()

const {t} = useI18n()

const CHOICES: readonly PronounChoice[] = ['MALE', 'FEMALE', 'NAME', 'OWN']

/** Kept while words of its own are being typed, which read as the name until the first one is there. */
const ownPicked = ref(false)

const choice = computed<PronounChoice>(() => ownPicked.value ? 'OWN' : choiceOf(pronouns.value))

function pick(value: string | number | null | undefined) {
  const picked = value as PronounChoice
  ownPicked.value = picked === 'OWN'
  pronouns.value = pronounsFor(picked, pronouns.value)
}

function wordOf(language: string, role: PronounRole): string {
  return pronouns.value?.[language]?.[role] ?? ''
}

function setWord(language: string, role: PronounRole, word: string | null | undefined) {
  const current = pronouns.value ?? {}
  const set: PronounSet = current[language] ?? {subject: null, object: null, dative: null, possessive: null}
  pronouns.value = {...current, [language]: {...set, [role]: word?.trim() ? word : null}}
}
</script>

<template>
  <div class="space-y-2 rounded-lg border border-(--border) p-3" :data-testid="`gender-answer-${answer}`">
    <LabelledField :label="answer">
      <SelectInput :model-value="choice" data-testid="gender-pronoun-choice" @update:model-value="pick">
        <option v-for="option in CHOICES" :key="option" :value="option">{{ t(`membersConfig.gender.choice.${option}`) }}</option>
      </SelectInput>
    </LabelledField>
    <div v-if="choice === 'OWN'" class="space-y-2">
      <div v-for="entry in PRONOUN_LANGUAGES" :key="entry.language" class="grid gap-2 sm:grid-cols-4">
        <LabelledField v-for="role in entry.roles" :key="role"
                       :label="t(`membersConfig.gender.role.${entry.language}.${role}`)">
          <TextInput :model-value="wordOf(entry.language, role)" maxlength="40"
                     @update:model-value="word => setWord(entry.language, role, word)"/>
        </LabelledField>
      </div>
    </div>
  </div>
</template>
