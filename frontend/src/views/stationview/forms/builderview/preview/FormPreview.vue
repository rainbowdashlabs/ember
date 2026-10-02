/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import MutedText from '@/components/typography/MutedText.vue'
import PublicPageFields from '@/components/forms/fill/PublicPageFields.vue'
import FormPageNav from '@/components/forms/fill/FormPageNav.vue'
import { useFormWalk } from '@/composables/useFormWalk'
import { usePublicAnswers } from '@/composables/usePublicAnswers'
import { presentQuestions } from '@/util/formShuffle'
import type { PublicFormPage, PublicFormQuestion } from '@/api/generated/schema'
import { pageLabel } from '../pageChoice'
import { questionConfigOf } from '../questionDefaults'
import { savedBranch, type FormLayoutEditor } from '../useFormLayout'

/**
 * The form as the reader will see it, built from what is in the editor, saved or not.
 *
 * <p>It walks the pages and follows the answers the way the real form does, with the same fields and
 * the same shuffling, and sends nothing. Starting over shuffles again, as the next reader would get
 * it. The path taken so far stands above it, which is how an editor finds a branch that sends people
 * to the wrong page before anybody answers.
 */
const props = defineProps<{
  layout: FormLayoutEditor
  shuffleQuestions: boolean
}>()

const { t } = useI18n()

const pages = ref<PublicFormPage[]>(props.layout.pages.value.map(page => ({
  key: page.key, title: page.title, description: page.description, after: { ...page.after },
})))
const asWritten: PublicFormQuestion[] = props.layout.pages.value
  .flatMap(page => page.questions.map(question => ({ page, question })))
  .map(({ page, question }, index) => ({
    id: index + 1,
    questionType: question.questionType,
    title: question.title,
    description: question.description,
    required: question.required,
    shuffle: question.shuffle,
    pageKey: page.key,
    config: questionConfigOf(question.questionType, question.config),
    branch: savedBranch(question),
  }))
const questions = ref<PublicFormQuestion[]>(presentQuestions(asWritten, props.shuffleQuestions))

const { answers, reset, toggleChoice, updateText, updateDate } = usePublicAnswers()
reset(questions.value)

const walk = useFormWalk(pages, questions, answers)

/** Whether the reader has pressed send, which here only says where the form would end. */
const sent = ref(false)

const pathLabel = computed(() => {
  const names = walk.path.value.map(key => {
    const index = pages.value.findIndex(page => page.key === key)
    const page = props.layout.pages.value[index]
    return page ? pageLabel(page, index, t) : key
  })
  if (sent.value) names.push(t('forms.preview.sent'))
  return names.join(', ')
})

function send() {
  if (walk.checkCurrent()) sent.value = true
}

function startOver() {
  questions.value = presentQuestions(asWritten, props.shuffleQuestions)
  reset(questions.value)
  walk.restart()
  sent.value = false
}
</script>

<template>
  <NeutralContainer class="space-y-4" data-testid="form-preview">
    <div class="flex flex-wrap items-center justify-between gap-2">
      <MutedText size="sm" data-testid="preview-path">{{ t('forms.preview.path', {path: pathLabel}) }}</MutedText>
      <SecondaryButton compact :icon="['fas', 'rotate-left']" @click="startOver">{{ t('forms.preview.startOver') }}</SecondaryButton>
    </div>

    <Alert v-if="sent" variant="success">{{ t('forms.preview.nothingSent') }}</Alert>

    <template v-else>
      <PublicPageFields :walk="walk" :answers="answers" framed
                        @update-text="updateText" @update-date="updateDate" @toggle-choice="toggleChoice"/>
      <FormPageNav :can-go-back="walk.pageNumber.value > 1" :is-last="walk.isLast.value"
                   :send-label="t('forms.submit')" @back="walk.back()" @next="walk.next()" @send="send"/>
    </template>
  </NeutralContainer>
</template>
