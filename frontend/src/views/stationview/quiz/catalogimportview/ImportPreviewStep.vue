/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import CsvQuestionPreview from '../csvimportview/CsvQuestionPreview.vue'
import NewCatalogFields from './NewCatalogFields.vue'
import ImportStepNav from './ImportStepNav.vue'
import type {ImportDraft} from '../csvimportview/quizCsvImport'
import type {QuizCatalogExportCategory} from '@/api/quiz'

const name = defineModel<string>('name', {required: true})
const description = defineModel<string>('description', {required: true})
const trainingEnabled = defineModel<boolean>('trainingEnabled', {required: true})

const props = defineProps<{
  drafts: ImportDraft[]
  categories: QuizCatalogExportCategory[]
  appending: boolean
  status: string
  loading: boolean
}>()

const emit = defineEmits<{
  back: []
  commit: []
}>()

const {t} = useI18n()

const includedCount = computed(() => props.drafts.filter(draft => draft.included).length)
</script>

<template>
  <NewCatalogFields
      v-if="!appending"
      v-model:name="name"
      v-model:description="description"
      v-model:training-enabled="trainingEnabled"
  />
  <CsvQuestionPreview :drafts="drafts" :categories="categories" :status="status" />
  <ImportStepNav
      :label="`${t('csvImport.import')} (${includedCount})`"
      icon="file-import"
      :loading="loading"
      @back="emit('back')"
      @advance="emit('commit')"
  />
</template>
