/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import PagePager from '@/components/documents/PagePager.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import {PdfFieldKind} from '@/api/generated/schema'
import {ZOOM_STEPS, zoomStep} from './pdfZoom'

/**
 * Turning the pages of the PDF, and putting a new field on the page shown: a text, a check mark, a
 * signature field, or a field a signer fills in when signing. A new field lands in the middle of the page, ready to be dragged where it belongs.
 *
 * <p>The page zooms in steps for placing a field exactly; the middle button names the zoom and brings
 * the whole page back into view.
 */
const props = defineProps<{
  page: number
  pageCount: number
  canAdd: boolean
  /** How far the page is zoomed in, 1 being the whole page in view. */
  zoom: number
}>()

const emit = defineEmits<{
  page: [page: number]
  add: [kind: PdfFieldKind]
  zoom: [zoom: number]
}>()

const {t} = useI18n()

const percent = computed(() => Math.round(props.zoom * 100))
const smallest = computed(() => props.zoom <= (ZOOM_STEPS[0] ?? 1))
const largest = computed(() => props.zoom >= (ZOOM_STEPS.at(-1) ?? 1))

const kinds: readonly {kind: PdfFieldKind; icon: [string, string]}[] = [
  {kind: PdfFieldKind.TEXT, icon: ['fas', 'font']},
  {kind: PdfFieldKind.CHECK, icon: ['fas', 'xmark']},
  {kind: PdfFieldKind.SIGNATURE, icon: ['fas', 'signature']},
  {kind: PdfFieldKind.FILL_IN, icon: ['fas', 'pen-to-square']},
]
</script>

<template>
  <div class="flex flex-wrap items-center justify-between gap-3">
    <PagePager :page="page" :page-count="pageCount" :label="t('documentTemplates.pageOf', {page, count: pageCount})"
               data-testid="pdf-page" @update:page="emit('page', $event)"/>
    <div class="flex items-center gap-1" data-testid="pdf-zoom">
      <IconButton :icon="['fas', 'magnifying-glass-minus']" :label="t('documentTemplates.zoomOut')" :disabled="smallest"
                  data-testid="pdf-zoom-out" @click="emit('zoom', zoomStep(zoom, -1))"/>
      <SecondaryButton compact :title="t('documentTemplates.zoomReset', {percent})" data-testid="pdf-zoom-reset"
                       @click="emit('zoom', 1)">{{ percent }} %</SecondaryButton>
      <IconButton :icon="['fas', 'magnifying-glass-plus']" :label="t('documentTemplates.zoomIn')" :disabled="largest"
                  data-testid="pdf-zoom-in" @click="emit('zoom', zoomStep(zoom, 1))"/>
    </div>
    <div class="flex flex-wrap gap-2">
      <SecondaryButton v-for="entry in kinds" :key="entry.kind" :icon="entry.icon" :disabled="!canAdd"
                       :data-testid="`pdf-add-${entry.kind.toLowerCase()}`" @click="emit('add', entry.kind)">
        {{ t(`documentTemplates.fieldKind.${entry.kind}`) }}
      </SecondaryButton>
    </div>
  </div>
</template>
