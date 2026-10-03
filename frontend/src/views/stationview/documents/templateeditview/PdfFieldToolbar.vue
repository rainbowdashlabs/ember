/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import {PdfFieldKind} from '@/api/generated/schema'

/**
 * Turning the pages of the PDF, and putting a new field on the page shown: a text, a check mark, or a
 * signature field. A new field lands in the middle of the page, ready to be dragged where it belongs.
 */
defineProps<{
  page: number
  pageCount: number
  canAdd: boolean
}>()

const emit = defineEmits<{
  page: [page: number]
  add: [kind: PdfFieldKind]
}>()

const {t} = useI18n()

const kinds: readonly {kind: PdfFieldKind; icon: [string, string]}[] = [
  {kind: PdfFieldKind.TEXT, icon: ['fas', 'font']},
  {kind: PdfFieldKind.CHECK, icon: ['fas', 'xmark']},
  {kind: PdfFieldKind.SIGNATURE, icon: ['fas', 'signature']},
]
</script>

<template>
  <div class="flex flex-wrap items-center justify-between gap-3">
    <div class="flex items-center gap-2">
      <IconButton :icon="['fas', 'chevron-left']" :label="t('common.previous')" :disabled="page <= 1" @click="emit('page', page - 1)"/>
      <span class="text-sm" data-testid="pdf-page">{{ t('documentTemplates.pageOf', {page, count: pageCount}) }}</span>
      <IconButton :icon="['fas', 'chevron-right']" :label="t('common.next')" :disabled="page >= pageCount" @click="emit('page', page + 1)"/>
    </div>
    <div class="flex flex-wrap gap-2">
      <SecondaryButton v-for="entry in kinds" :key="entry.kind" :icon="entry.icon" :disabled="!canAdd"
                       :data-testid="`pdf-add-${entry.kind.toLowerCase()}`" @click="emit('add', entry.kind)">
        {{ t(`documentTemplates.fieldKind.${entry.kind}`) }}
      </SecondaryButton>
    </div>
  </div>
</template>
