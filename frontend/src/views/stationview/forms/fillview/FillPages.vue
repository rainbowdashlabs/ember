/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import FormPageIntro from '@/components/forms/fill/FormPageIntro.vue'
import FormPageNav from '@/components/forms/fill/FormPageNav.vue'
import QuestionCard from './QuestionCard.vue'
import type {FormAnswerValue, FormPage, FormQuestion} from '@/api/generated/schema'
import type {useFormWalk} from '@/composables/useFormWalk'

/** The page of a station form the member is on, its questions, and the buttons to go on or send. */
defineProps<{
  walk: ReturnType<typeof useFormWalk<FormPage, FormQuestion>>
  /** What the button that sends the form says, which differs for a first answer and a correction. */
  sendLabel: string
}>()

const answers = defineModel<Record<number, FormAnswerValue>>('answers', {required: true})

const emit = defineEmits<{
  send: []
  next: []
  cancel: []
}>()

const {t} = useI18n()
</script>

<template>
  <FormPageIntro v-if="walk.paged.value" :page-number="walk.pageNumber.value" :progress="walk.progress.value"
                 :title="walk.currentPage.value?.title" :description="walk.currentPage.value?.description"/>

  <div class="space-y-4">
    <QuestionCard v-for="q in walk.currentQuestions.value" :key="q.id"
                  v-model="answers[q.id]"
                  :question="q" :error="walk.errors.value[q.id]"/>
  </div>

  <FormPageNav :can-go-back="walk.pageNumber.value > 1" :is-last="walk.isLast.value" :send-label="sendLabel"
               @back="walk.back()" @next="emit('next')" @send="emit('send')">
    <SecondaryButton @click="emit('cancel')">{{ t('common.cancel') }}</SecondaryButton>
  </FormPageNav>
</template>
