/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import FormatFieldTable from './FormatFieldTable.vue'
import QuestionTypeTable from './QuestionTypeTable.vue'
import FormatTemplateDownloads from './FormatTemplateDownloads.vue'
import {CSV_COLUMNS, JSON_CATALOG_FIELDS, JSON_CATEGORY_FIELDS, JSON_QUESTION_FIELDS} from './catalogFormatFields'

defineProps<{
  offerDownloads: boolean
}>()

const {t} = useI18n()

const showing = ref<'csv' | 'json'>('csv')
</script>

<template>
  <div class="space-y-4">
    <div class="flex items-center gap-1 flex-wrap">
      <SelectionToggleButton :selected="showing === 'csv'" @toggle="showing = 'csv'">
        {{ t('quiz.format.sheetTab') }}
      </SelectionToggleButton>
      <SelectionToggleButton :selected="showing === 'json'" @toggle="showing = 'json'">
        {{ t('quiz.format.fileTab') }}
      </SelectionToggleButton>
    </div>

    <template v-if="showing === 'csv'">
      <p class="text-sm">{{ t('quiz.format.sheetIntro') }}</p>
      <FormatFieldTable :title="t('quiz.format.columnsTitle')" :fields="CSV_COLUMNS" />
      <QuestionTypeTable :show-config="false" />
    </template>

    <template v-else>
      <p class="text-sm">{{ t('quiz.format.fileIntro') }}</p>
      <FormatFieldTable :title="t('quiz.format.catalogTitle')" :fields="JSON_CATALOG_FIELDS" />
      <FormatFieldTable :title="t('quiz.format.categoriesTitle')" :fields="JSON_CATEGORY_FIELDS" />
      <FormatFieldTable :title="t('quiz.format.questionsTitle')" :fields="JSON_QUESTION_FIELDS" />
      <QuestionTypeTable :show-config="true" />
    </template>

    <FormatTemplateDownloads v-if="offerDownloads" :format="showing" />
  </div>
</template>
