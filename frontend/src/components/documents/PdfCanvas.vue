/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onUnmounted, ref, watch} from 'vue'
import type {PDFDocumentLoadingTask, PDFDocumentProxy, RenderTask} from 'pdfjs-dist'

/**
 * A page of a PDF, drawn onto a canvas.
 *
 * <p>Drawing the pages ourselves is what makes a PDF readable on a phone at all. Handing the file to
 * the browser, as a download or in a frame, asks it for a viewer it does not have there: it offers to
 * save the file instead, or shows an empty box, and on a browser embedded in another app it does
 * neither and the reader is left looking at nothing.
 *
 * <p>One page is held at a time, because a long report drawn in full is more than a phone will carry.
 * Which page that is belongs to whoever placed the canvas, so a slide deck and a preview can want
 * different things from the same document without this knowing about either.
 */
const props = defineProps<{
  /** The document's bytes. Nothing is drawn until they arrive. */
  source: Blob | ArrayBuffer | null
  /** Which page to draw, counted from one. */
  page?: number
  /** How much of the available room the page may fill, for a caller that has to fit controls beside it. */
  scale?: number
}>()

const emit = defineEmits<{loaded: [pageCount: number]; failed: [error: unknown]}>()

const canvas = ref<HTMLCanvasElement | null>(null)

let pdfDoc: PDFDocumentProxy | null = null
let loadingTask: PDFDocumentLoadingTask | null = null
let renderTask: RenderTask | null = null
let generation = 0

/**
 * The room a page is drawn into, measured from whatever holds the canvas.
 *
 * <p>Falls back to the window where the canvas has no laid-out parent to ask, which is what a
 * full-screen viewer amounts to anyway.
 */
function available(): {width: number; height: number} {
    const parent = canvas.value?.parentElement
    const width = parent?.clientWidth || window.innerWidth
    const height = parent?.clientHeight || window.innerHeight
    return {width, height}
}

/** pdf.js takes ownership of the memory it is handed, so it never gets the caller's own copy. */
async function bytesOf(source: Blob | ArrayBuffer): Promise<Uint8Array> {
    const buffer = source instanceof Blob ? await source.arrayBuffer() : source
    return new Uint8Array(buffer.slice(0))
}

function releaseDocument() {
    renderTask?.cancel()
    renderTask = null
    pdfDoc = null
    loadingTask?.destroy()
    loadingTask = null
}

async function load() {
    const mine = ++generation
    releaseDocument()
    if (!props.source) return
    try {
        const pdfjs = await import('pdfjs-dist')
        pdfjs.GlobalWorkerOptions.workerSrc = new URL('pdfjs-dist/build/pdf.worker.mjs', import.meta.url).href
        const data = await bytesOf(props.source)
        if (mine !== generation) return
        const task = pdfjs.getDocument({data})
        const doc = await task.promise
        if (mine !== generation) {
            task.destroy()
            return
        }
        loadingTask = task
        pdfDoc = doc
        emit('loaded', doc.numPages)
        await draw(mine)
    } catch (error) {
        if (mine === generation) emit('failed', error)
    }
}

async function draw(mine: number) {
    const canvasEl = canvas.value
    const context = canvasEl?.getContext('2d')
    if (!pdfDoc || !canvasEl || !context) return

    const wanted = Math.min(Math.max(props.page ?? 1, 1), pdfDoc.numPages)
    const page = await pdfDoc.getPage(wanted)
    if (mine !== generation) return

    const unscaled = page.getViewport({scale: 1})
    const room = available()
    const fit = Math.min(room.width / unscaled.width, room.height / unscaled.height)
    const viewport = page.getViewport({scale: fit * (props.scale ?? 1)})

    renderTask?.cancel()
    canvasEl.width = viewport.width
    canvasEl.height = viewport.height
    context.clearRect(0, 0, canvasEl.width, canvasEl.height)
    renderTask = page.render({canvas: canvasEl, canvasContext: context, viewport})
    try {
        await renderTask.promise
    } catch {
        renderTask = null
    }
}

watch(() => props.source, load, {immediate: true})
watch([() => props.page, () => props.scale], () => draw(generation))
onUnmounted(() => {
    generation++
    releaseDocument()
})
</script>

<template>
  <canvas ref="canvas" data-testid="pdf-canvas"/>
</template>
