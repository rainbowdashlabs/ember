/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import RadioInput from '@/components/input/toggle/RadioInput.vue'
import type {ExportSeparator} from '@/util/exportFormat'

/**
 * What goes between the cells of a spreadsheet.
 *
 * <p>Asked wherever a spreadsheet can be had, and asked the same way, because guessing wrong puts
 * every row into a single column and reaches us as "the export is broken" rather than as a question
 * about separators. The semicolon is what German spreadsheet software expects and is the default.
 */
const separator = defineModel<ExportSeparator>({required: true})

const {t} = useI18n()
</script>

<template>
  <div class="space-y-2">
    <FieldLabel>{{ t('exportFormat.separator') }}</FieldLabel>
    <div class="flex items-center gap-4">
      <FieldLabel inline class="cursor-pointer">
        <RadioInput v-model="separator" value="semicolon" data-testid="export-separator-semicolon"/>
        {{ t('exportFormat.semicolon') }}
      </FieldLabel>
      <FieldLabel inline class="cursor-pointer">
        <RadioInput v-model="separator" value="comma" data-testid="export-separator-comma"/>
        {{ t('exportFormat.comma') }}
      </FieldLabel>
    </div>
    <MutedText size="sm">{{ t('exportFormat.separatorHint') }}</MutedText>
  </div>
</template>
