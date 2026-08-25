/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import {QUESTION_TYPE_FORMATS} from './catalogFormatFields'

defineProps<{
  showConfig: boolean
}>()

const {t} = useI18n()
</script>

<template>
  <div class="space-y-2">
    <SectionHeader>{{ t('quiz.format.typesTitle') }}</SectionHeader>
    <div class="overflow-x-auto">
      <table class="w-full text-xs">
        <tbody>
          <tr v-for="entry in QUESTION_TYPE_FORMATS" :key="entry.type" class="align-top">
            <td class="py-1 pr-3 whitespace-nowrap font-medium">{{ t(`quiz.questionTypes.${entry.type}`) }}</td>
            <td class="py-1 pr-3 font-mono text-(--text-muted)">{{ entry.spellings }}</td>
            <td class="py-1 font-mono break-all">{{ showConfig ? entry.config : entry.csvAnswer }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <p class="text-xs text-(--text-muted)">
      {{ showConfig ? t('quiz.format.typesConfigHint') : t('quiz.format.typesAnswerHint') }}
    </p>
  </div>
</template>
