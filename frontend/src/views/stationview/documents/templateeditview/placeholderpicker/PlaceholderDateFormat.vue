/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {DateKind, type DateFormatOption, type DocumentLanguage, type Placeholder} from '@/api/generated/schema'
import BaseButton from '@/components/button/BaseButton.vue'
import {compileDateFormat} from '@/util/dateFormatPattern'
import {exampleOf, presetPattern} from './placeholderDates'
import PlaceholderOwnDateFormat from './PlaceholderOwnDateFormat.vue'
import PlaceholderRow from './PlaceholderRow.vue'

/**
 * The last step of the picker for a date: the ready-made formats, each shown on the example day with its
 * tokens beside it, and an own format below them. Choosing one inserts the date in that format; the
 * button at the top leads back to where the date was picked.
 */
const props = defineProps<{
  placeholder: Placeholder
  formats: DateFormatOption[]
  language: DocumentLanguage
}>()

const emit = defineEmits<{
  choose: [format: string]
  back: []
}>()

const {t} = useI18n()

function example(option: DateFormatOption): string {
  return exampleOf(compileDateFormat(presetPattern(option, props.language)), props.language) ?? option.written
}
</script>

<template>
  <div class="space-y-2" data-testid="placeholder-date-format">
    <div class="flex flex-wrap items-center gap-2 text-sm">
      <BaseButton compact :icon="['fas', 'chevron-left']" class="!font-normal !text-(--text-muted) hover:bg-(--bg-accent)"
                  data-testid="placeholder-date-back" @click="emit('back')">
        {{ t('common.back') }}
      </BaseButton>
      <span class="font-medium">{{ t('documentTemplates.dateFormat.title', {name: placeholder.label}) }}</span>
    </div>
    <ul class="grid gap-0.5 sm:grid-cols-2">
      <li v-for="option in formats" :key="option.preset">
        <PlaceholderRow :name="example(option)" :branch="false" :detail="presetPattern(option, language)"
                        :data-testid="`placeholder-date-${option.written}`" @choose="emit('choose', option.written)"/>
      </li>
    </ul>
    <PlaceholderOwnDateFormat :clock="placeholder.dateKind === DateKind.DATE_TIME" :language="language"
                              @choose="format => emit('choose', format)"/>
  </div>
</template>
