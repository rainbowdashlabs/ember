/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import type {FormatField} from './catalogFormatFields'

defineProps<{
  title: string
  fields: FormatField[]
}>()

const {t} = useI18n()
</script>

<template>
  <div class="space-y-2">
    <SectionHeader>{{ title }}</SectionHeader>
    <div class="overflow-x-auto">
      <table class="w-full text-xs">
        <tbody>
          <tr v-for="field in fields" :key="field.key" class="align-top">
            <td class="py-1 pr-3 font-mono whitespace-nowrap">{{ field.key }}</td>
            <td class="py-1 pr-3 whitespace-nowrap text-(--text-muted)">
              {{ field.required ? t('quiz.format.required') : t('quiz.format.optional') }}
            </td>
            <td class="py-1">{{ t(`quiz.format.fields.${field.i18n}`) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
