/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import LabelledField from '@/components/input/LabelledField.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {PronounForm, type ChoiceField} from '@/api/generated/schema'
import type {TemplateDraft} from './templateDraft'

/**
 * Which choice question the pronouns of the letter follow, and what each of its answers stands for:
 * "er", "sie", or the official first name wherever a pronoun stands. An empty answer, or one not
 * mapped, takes the fallback. Without a question the first name stands everywhere.
 */
const draft = defineModel<TemplateDraft>({required: true})

const props = defineProps<{
  choiceFields: ChoiceField[]
}>()

const {t} = useI18n()

const forms = [PronounForm.ER, PronounForm.SIE, PronounForm.NAME]

const field = computed(() => props.choiceFields.find(choice => choice.id === draft.value.pronounSource?.fieldId) ?? null)

function chooseField(id: string | number | null | undefined) {
  const fieldId = Number(id)
  draft.value.pronounSource = fieldId > 0 ? {fieldId, answers: {}, fallback: PronounForm.NAME} : null
}

function mapAnswer(answer: string, form: string | number | null | undefined) {
  const source = draft.value.pronounSource
  if (!source) return
  draft.value.pronounSource = {...source, answers: {...source.answers, [answer]: form as PronounForm}}
}

function setFallback(form: string | number | null | undefined) {
  const source = draft.value.pronounSource
  if (source) draft.value.pronounSource = {...source, fallback: form as PronounForm}
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SubHeader>{{ t('documentTemplates.pronounsTitle') }}</SubHeader>
    <MutedText size="sm" tag="p">{{ t('documentTemplates.pronounsHint') }}</MutedText>
    <LabelledField :label="t('documentTemplates.pronounField')">
      <SelectInput :model-value="draft.pronounSource?.fieldId ?? 0" data-testid="pronoun-field" @update:model-value="chooseField">
        <option :value="0">{{ t('documentTemplates.pronounFieldNone') }}</option>
        <option v-for="choice in choiceFields" :key="choice.id" :value="choice.id">{{ choice.name }}</option>
      </SelectInput>
    </LabelledField>
    <div v-if="field && draft.pronounSource" class="grid gap-3 sm:grid-cols-2">
      <LabelledField v-for="answer in field.options" :key="answer" :label="answer">
        <SelectInput :model-value="draft.pronounSource.answers[answer] ?? draft.pronounSource.fallback"
                     :data-testid="`pronoun-answer-${answer}`" @update:model-value="form => mapAnswer(answer, form)">
          <option v-for="form in forms" :key="form" :value="form">{{ t(`documentTemplates.pronounForm.${form}`) }}</option>
        </SelectInput>
      </LabelledField>
      <LabelledField :label="t('documentTemplates.pronounFallback')">
        <SelectInput :model-value="draft.pronounSource.fallback" @update:model-value="setFallback">
          <option v-for="form in forms" :key="form" :value="form">{{ t(`documentTemplates.pronounForm.${form}`) }}</option>
        </SelectInput>
      </LabelledField>
    </div>
  </NeutralContainer>
</template>
