/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'

defineProps<{
  catalogId: number
}>()

const emit = defineEmits<{
  create: []
}>()

const { t } = useI18n()
const router = useRouter()
</script>

<template>
  <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
    <SubHeader>{{ t('quiz.questions.title') }}</SubHeader>
    <div class="grid grid-cols-2 sm:flex gap-2">
      <SecondaryButton :icon="['fas', 'file-import']" @click="router.push({ name: 'quiz-catalog-import', params: { id: catalogId } })">
        {{ t('quiz.csv.import') }}
      </SecondaryButton>
      <SecondaryButton :icon="['fas', 'brain']" @click="router.push({ name: 'quiz-catalog-generate', params: { id: catalogId } })">
        {{ t('quiz.ai.generateQuestions') }}
      </SecondaryButton>
      <SecondaryButton :icon="['fas', 'brain']" @click="router.push({name: 'quiz-catalog-mc-fill', params: {id: catalogId}})">
        {{ t('quiz.ai.fillMcAnswers') }}
      </SecondaryButton>
      <PrimaryButton :icon="['fas', 'plus']" @click="emit('create')">
        {{ t('quiz.questions.create') }}
      </PrimaryButton>
    </div>
  </div>
</template>
