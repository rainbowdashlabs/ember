/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {nextTick, onUnmounted, ref, watch} from 'vue'
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
let drawTicket = 0

/**
 * Whether a draw has been overtaken, either by another document or by a later page of this one.
 *
 * <p>The document alone is not enough to tell: turning two pages quickly starts two draws of the
 * same document, and the page pdf.js already holds in memory answers before the page it has to
 * fetch. Without a ticket of its own the slower draw paints over the newer one and the canvas ends
 * on a page the pager is no longer showing.
 */
function overtaken(mine: number, ticket: number): boolean {
    return mine !== generation || ticket !== drawTicket
}

/**
 * Lets a running render finish letting go of the canvas.
 *
 * <p>Cancelling only asks it to stop: until its promise has settled, pdf.js still owns the canvas,
 * and resizing it or starting a second render there fails the new render outright. Waiting is what
 * makes a page turn during a draw safe.
 */
async function settleRender(): Promise<void> {
    const running = renderTask
    if (!running) return
    running.cancel()
    await running.promise.catch(() => undefined)
    if (renderTask === running) renderTask = null
}

/**
 * The room a page is drawn into, measured from whatever holds the canvas.
 *
 * <p>Falls back to the window where the canvas has no laid-out parent to ask, which is what a
 * full-screen viewer amounts to anyway. Whoever placed the canvas is given a tick to lay it out
 * before the first page is measured, since a reader that reveals it on hearing the page count has
 * not been drawn yet at the moment it says so, and a hidden parent measures nothing.
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
        await nextTick()
        if (mine !== generation) return
        await draw(mine)
    } catch (error) {
        if (mine === generation) emit('failed', error)
    }
}

async function draw(mine: number) {
    const ticket = ++drawTicket
    const canvasEl = canvas.value
    const context = canvasEl?.getContext('2d')
    if (!pdfDoc || !canvasEl || !context) return

    const wanted = Math.min(Math.max(props.page ?? 1, 1), pdfDoc.numPages)
    const page = await pdfDoc.getPage(wanted)
    if (overtaken(mine, ticket)) return

    await settleRender()
    if (overtaken(mine, ticket)) return

    const unscaled = page.getViewport({scale: 1})
    const room = available()
    const fit = Math.min(room.width / unscaled.width, room.height / unscaled.height)
    const viewport = page.getViewport({scale: fit * (props.scale ?? 1)})

    canvasEl.width = viewport.width
    canvasEl.height = viewport.height
    context.clearRect(0, 0, canvasEl.width, canvasEl.height)
    const task = page.render({canvas: canvasEl, canvasContext: context, viewport})
    renderTask = task
    try {
        await task.promise
    } catch {
        void 0
    } finally {
        if (renderTask === task) renderTask = null
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
