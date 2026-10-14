/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import DeleteButton from '@/components/button/DeleteButton.vue'
import LabelledField from '@/components/input/LabelledField.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleSetting from '@/components/input/toggle/ToggleSetting.vue'
import {TemplateQuestionKind, type Placeholder, type TemplateQuestion} from '@/api/generated/schema'
import PlaceholderTextInput from './placeholderpicker/PlaceholderTextInput.vue'
import QuestionLimitsEditor from './questionlimits/QuestionLimitsEditor.vue'
import {ANSWER_PREFIX, QUESTION_KINDS, keyFromLabel, limitsFor} from './templateQuestions'

/**
 * One question of a template: what the person generating is asked, what kind of answer it takes, what
 * counts as an answer, what it suggests for the member and whether it has to be answered. Changing the
 * kind starts the limits over, since the limits of one kind say nothing about another.
 *
 * <p>The question is named in the template as `{{ask.<name>}}`. Its name follows its label for as long
 * as nothing in the template names it yet, so a new question gets a readable name without being typed
 * twice; once the template names it, the name stays and can only be changed by hand.
 */
const question = defineModel<TemplateQuestion>({required: true})

const props = defineProps<{
  /** What the suggestion can name: the owner's values, never an answer. */
  placeholders: Placeholder[]
  legal: boolean
  /** The names of the other questions of the template. */
  taken: string[]
  /** Whether something in the template names this question's answer. */
  named: boolean
}>()

const emit = defineEmits<{
  remove: []
}>()

const {t} = useI18n()

const written = computed(() => `{{${ANSWER_PREFIX}${question.value.key}}}`)

function setLabel(written: string | undefined) {
  const label = written ?? ''
  const follows = !props.named && question.value.key === keyFromLabel(question.value.label, props.taken)
  question.value = {...question.value, label, key: follows ? keyFromLabel(label, props.taken) : question.value.key}
}

function setKind(written: string | number | null | undefined) {
  const kind = QUESTION_KINDS.find(candidate => candidate === written) ?? TemplateQuestionKind.TEXT
  question.value = {...question.value, kind, limits: limitsFor(kind)}
}
</script>

<template>
  <div class="grid w-full gap-3 sm:grid-cols-2" data-testid="template-question">
    <LabelledField :label="t('documentTemplates.questions.label')">
      <TextInput :model-value="question.label" :placeholder="t('documentTemplates.questions.labelPlaceholder')"
                 data-testid="template-question-label" @update:model-value="setLabel"/>
    </LabelledField>
    <LabelledField :label="t('documentTemplates.questions.kind')">
      <SelectInput :model-value="question.kind" data-testid="template-question-kind" @update:model-value="setKind">
        <option v-for="kind in QUESTION_KINDS" :key="kind" :value="kind">{{ t(`documentTemplates.questions.kindName.${kind}`) }}</option>
      </SelectInput>
    </LabelledField>
    <LabelledField :label="t('documentTemplates.questions.suggestion')" :help="t(`documentTemplates.questions.suggestionHelp.${question.kind}`)">
      <PlaceholderTextInput v-model="question.suggestion" :placeholders="placeholders" :legal="legal"
                            :prompt="t('documentTemplates.questions.suggestionPlaceholder')"/>
    </LabelledField>
    <LabelledField :label="t('documentTemplates.questions.key')" :help="t('documentTemplates.questions.keyHelp', {written})">
      <TextInput v-model="question.key" data-testid="template-question-key"/>
    </LabelledField>
    <QuestionLimitsEditor v-model="question.limits" :kind="question.kind" class="sm:col-span-2"/>
    <ToggleSetting v-model="question.required" :label="t('documentTemplates.questions.required')"
                   :hint="t('documentTemplates.questions.requiredHint')"/>
    <div class="flex items-end justify-end">
      <DeleteButton data-testid="template-question-remove" @click="emit('remove')"/>
    </div>
  </div>
</template>
