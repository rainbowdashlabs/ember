/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import type {PageViewport} from 'pdfjs-dist'
import PdfCanvas from '@/components/documents/PdfCanvas.vue'
import {labelledText, type PlaceholderLabels} from '@/components/input/markdowneditor/placeholderChip'
import {PdfFieldKind, type PdfField} from '@/api/generated/schema'
import PdfFieldBox from './PdfFieldBox.vue'
import {movedBy, resizedBy} from './pdfFields'
import {screenBoxOf, type PageGeometry} from './pdfViewport'

/**
 * One page of the PDF with the fields on it laid over it, where they will print.
 *
 * <p>The page is drawn by {@link PdfCanvas}; the viewport it announces maps every stored box onto the
 * canvas and every drag back into the page's points, turned and cropped pages included. Fields of other
 * pages are not shown here; the list beside the page reaches all of them.
 */
const props = defineProps<{
  source: Blob
  page: number
  fields: PdfField[]
  selected: number | null
  labels: PlaceholderLabels
}>()

const emit = defineEmits<{
  loaded: [pageCount: number]
  drawn: [geometry: PageGeometry]
  select: [index: number]
  change: [index: number, field: PdfField]
}>()

const {t} = useI18n()
const geometry = ref<PageGeometry | null>(null)

const shown = computed(() => props.fields
    .map((field, index) => ({field, index}))
    .filter(entry => entry.field.rect.page === props.page))

const overlay = computed(() => geometry.value
    ? {width: `${geometry.value.width}px`, height: `${geometry.value.height}px`}
    : {})

function drawn(viewport: PageViewport) {
  geometry.value = {transform: [...viewport.transform], width: viewport.width, height: viewport.height}
  emit('drawn', geometry.value)
}

/** What a box says about itself: the signer, or the text with its placeholders by name. */
function labelOf(field: PdfField): string {
  if (field.kind === PdfFieldKind.SIGNATURE) return t(`documentTemplates.signer.${field.role ?? 'PARTICIPANT'}`)
  return labelledText(field.text ?? '', props.labels) || t(`documentTemplates.fieldKind.${field.kind}`)
}

function move(index: number, field: PdfField, dx: number, dy: number) {
  if (geometry.value) emit('change', index, movedBy(geometry.value, field, dx, dy))
}

function resize(index: number, field: PdfField, dw: number, dh: number) {
  if (geometry.value) emit('change', index, resizedBy(geometry.value, field, dw, dh))
}
</script>

<template>
  <div class="relative h-[75vh] w-full overflow-auto rounded-lg border border-(--border) bg-white" data-testid="pdf-field-canvas">
    <PdfCanvas class="block" :source="source" :page="page" @loaded="count => emit('loaded', count)" @drawn="drawn"/>
    <div v-if="geometry" class="absolute left-0 top-0" :style="overlay">
      <PdfFieldBox v-for="entry in shown" :key="entry.index" :box="screenBoxOf(geometry, entry.field.rect)"
                   :kind="entry.field.kind" :label="labelOf(entry.field)" :selected="selected === entry.index"
                   @select="emit('select', entry.index)"
                   @move="(dx, dy) => move(entry.index, entry.field, dx, dy)"
                   @resize="(dw, dh) => resize(entry.index, entry.field, dw, dh)"/>
    </div>
  </div>
</template>
