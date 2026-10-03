/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {LetterCell, Placeholder} from '@/api/generated/schema'
import LetterCellEditor from './LetterCellEditor.vue'
import {emptyCell, MAX_CELLS} from './templateDraft'

/**
 * The header or the footer of the letter: a row of up to three cells, left to right, each taking an
 * equal share of the width.
 */
const cells = defineModel<LetterCell[]>({required: true})

defineProps<{
  title: string
  placeholders: Placeholder[]
  legal: boolean
}>()

const {t} = useI18n()

function replace(index: number, cell: LetterCell) {
  cells.value = cells.value.map((current, at) => at === index ? cell : current)
}

function remove(index: number) {
  cells.value = cells.value.filter((_cell, at) => at !== index)
}
</script>

<template>
  <div class="space-y-3">
    <div class="flex flex-wrap items-center justify-between gap-2">
      <SubHeader>{{ title }}</SubHeader>
      <SecondaryButton :icon="['fas', 'plus']" :disabled="cells.length >= MAX_CELLS" data-testid="letter-cell-add"
                       @click="cells = [...cells, emptyCell()]">
        {{ t('documentTemplates.addCell') }}
      </SecondaryButton>
    </div>
    <MutedText v-if="cells.length === 0" size="sm" tag="p">{{ t('documentTemplates.noCells') }}</MutedText>
    <div class="grid gap-3 lg:grid-cols-3">
      <LetterCellEditor
          v-for="(cell, index) in cells"
          :key="index"
          :model-value="cell"
          :placeholders="placeholders"
          :legal="legal"
          @update:model-value="value => replace(index, value)"
          @remove="remove(index)"
      />
    </div>
  </div>
</template>
