/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import PagePager from '@/components/documents/PagePager.vue'
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
    <PagePager :page="page" :page-count="pageCount" :label="t('documentTemplates.pageOf', {page, count: pageCount})"
               data-testid="pdf-page" @update:page="emit('page', $event)"/>
    <div class="flex flex-wrap gap-2">
      <SecondaryButton v-for="entry in kinds" :key="entry.kind" :icon="entry.icon" :disabled="!canAdd"
                       :data-testid="`pdf-add-${entry.kind.toLowerCase()}`" @click="emit('add', entry.kind)">
        {{ t(`documentTemplates.fieldKind.${entry.kind}`) }}
      </SecondaryButton>
    </div>
  </div>
</template>
