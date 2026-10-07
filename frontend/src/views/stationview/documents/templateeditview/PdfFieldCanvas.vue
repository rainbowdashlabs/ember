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
import {PdfFieldKind, type FormBinding, type FormField, type PdfField} from '@/api/generated/schema'
import FormFieldMarker from './FormFieldMarker.vue'
import PdfFieldBox from './PdfFieldBox.vue'
import {movedBy, resizedBy} from './pdfFields'
import {scaleOf, screenBoxOf, type PageGeometry, type ScreenBox} from './pdfViewport'
import {fieldTextLook} from './pdfFieldLook'
import {useInjectedEditorFonts} from '@/composables/useEditorFonts'
import {usePagePan} from './usePagePan'

/**
 * One page of the PDF with the fields on it laid over it, where they will print.
 *
 * <p>The page is drawn by {@link PdfCanvas}; the viewport it announces maps every stored box onto the
 * canvas and every drag back into the page's points, turned and cropped pages included. Fields of other
 * pages are not shown here; the list beside the page reaches all of them. The PDF's own form fields
 * are outlined underneath, named by what fills them, and pressing one chooses its entry below the page.
 *
 * <p>Zoomed in, the page is dragged around by a press beside the fields ({@link usePagePan}).
 */
const props = defineProps<{
  source: Blob
  page: number
  /** How far the page is zoomed in, 1 being the whole page in view; larger pages scroll. */
  zoom: number
  fields: PdfField[]
  selected: number | null
  labels: PlaceholderLabels
  /** The PDF's own form fields, outlined where they sit so their entries below the page can be found. */
  formFields: FormField[]
  formBindings: FormBinding[]
  /** The form field whose entry is chosen, by name. */
  chosenFormField: string | null
}>()

const emit = defineEmits<{
  loaded: [pageCount: number]
  drawn: [geometry: PageGeometry]
  select: [index: number]
  change: [index: number, field: PdfField]
  chooseFormField: [name: string]
}>()

const {t} = useI18n()
const editorFonts = useInjectedEditorFonts()
const geometry = ref<PageGeometry | null>(null)
const scroller = ref<HTMLElement | null>(null)
const panning = usePagePan(scroller)

/** The part of the drawn page in view, which zoomed in is the part scrolled to. */
function visibleBox(): ScreenBox | undefined {
  const box = scroller.value
  const page = geometry.value
  if (!box || !page) return undefined
  return {
    left: box.scrollLeft,
    top: box.scrollTop,
    width: Math.min(box.clientWidth, page.width - box.scrollLeft),
    height: Math.min(box.clientHeight, page.height - box.scrollTop),
  }
}

defineExpose({visibleBox})

function lookOf(field: PdfField, page: PageGeometry) {
  return fieldTextLook(field, scaleOf(page), family => editorFonts?.fontFamily(family))
}

const shown = computed(() => props.fields
    .map((field, index) => ({field, index}))
    .filter(entry => entry.field.rect.page === props.page))

const shownFormFields = computed(() => props.formFields
    .flatMap(field => field.rect && field.rect.page === props.page ? [{field, rect: field.rect}] : []))

function bindingOf(field: FormField): string {
  return props.formBindings.find(binding => binding.fieldName === field.name)?.text ?? ''
}

/** What a form field's outline says: what the template fills it with, else what the PDF calls it. */
function formLabelOf(field: FormField): string {
  return labelledText(bindingOf(field), props.labels) || field.tooltip || field.name
}

const overlay = computed(() => geometry.value
    ? {width: `${geometry.value.width}px`, height: `${geometry.value.height}px`}
    : {})

function drawn(viewport: PageViewport) {
  geometry.value = {transform: [...viewport.transform], width: viewport.width, height: viewport.height}
  emit('drawn', geometry.value)
}

/** What a box says about itself: its text with the placeholders by name, or else the signer or its kind. */
function labelOf(field: PdfField): string {
  const text = labelledText(field.text ?? '', props.labels)
  if (field.kind === PdfFieldKind.SIGNATURE) return text || t(`documentTemplates.signer.${field.role ?? 'PARTICIPANT'}`)
  return text || t(`documentTemplates.fieldKind.${field.kind}`)
}

function move(index: number, field: PdfField, dx: number, dy: number) {
  if (geometry.value) emit('change', index, movedBy(geometry.value, field, dx, dy))
}

function resize(index: number, field: PdfField, dw: number, dh: number) {
  if (geometry.value) emit('change', index, resizedBy(geometry.value, field, dw, dh))
}
</script>

<template>
  <div ref="scroller" class="relative h-[75vh] w-full overflow-auto rounded-lg border border-(--border) bg-white select-none"
       :class="panning ? 'cursor-grabbing' : 'cursor-grab'" data-testid="pdf-field-canvas">
    <PdfCanvas class="block" :source="source" :page="page" :scale="zoom" @loaded="count => emit('loaded', count)" @drawn="drawn"/>
    <div v-if="geometry" class="absolute left-0 top-0" :style="overlay">
      <FormFieldMarker v-for="entry in shownFormFields" :key="entry.field.name" :box="screenBoxOf(geometry, entry.rect)"
                       :label="formLabelOf(entry.field)" :bound="bindingOf(entry.field) !== ''"
                       :chosen="chosenFormField === entry.field.name" @choose="emit('chooseFormField', entry.field.name)"/>
      <PdfFieldBox v-for="entry in shown" :key="entry.index" :box="screenBoxOf(geometry, entry.field.rect)"
                   :kind="entry.field.kind" :label="labelOf(entry.field)" :selected="selected === entry.index"
                   :look="lookOf(entry.field, geometry)"
                   @select="emit('select', entry.index)"
                   @move="(dx, dy) => move(entry.index, entry.field, dx, dy)"
                   @resize="(dw, dh) => resize(entry.index, entry.field, dw, dh)"/>
    </div>
  </div>
</template>
