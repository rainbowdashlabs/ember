/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, computed, onMounted, onUnmounted, nextTick} from 'vue'
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import client from '@/api/client'

const {t} = useI18n()
const props = defineProps<{
  contentUrl: string
  title: string
}>()
const emit = defineEmits<{ close: [] }>()

const canvas = ref<HTMLCanvasElement | null>(null)
const currentPage = ref(1)
const totalPages = ref(0)
const loading = ref(true)
const errorMsg = ref('')
const controlsVisible = ref(true)
let hideTimeout: ReturnType<typeof setTimeout> | null = null
let pdfDoc: any = null

function showControls() {
  controlsVisible.value = true
  resetHideTimer()
}
function resetHideTimer() {
  if (hideTimeout) clearTimeout(hideTimeout)
  hideTimeout = setTimeout(() => { controlsVisible.value = false }, 2500)
}

const canvasScale = computed(() => controlsVisible.value ? 0.9 : 1)

async function loadPdf() {
  loading.value = true
  errorMsg.value = ''
  try {
    const pdfjsLib = await import('pdfjs-dist')
    pdfjsLib.GlobalWorkerOptions.workerSrc = new URL('pdfjs-dist/build/pdf.worker.mjs', import.meta.url).href
    const res = await client.get<ArrayBuffer>(props.contentUrl, {responseType: 'arraybuffer'})
    pdfDoc = (await pdfjsLib.getDocument({data: new Uint8Array(res.data)}).promise)
    totalPages.value = pdfDoc.numPages
    if (totalPages.value > 0) await renderPage(1)
  } catch (e) {
    console.error('PDF load failed', e)
    errorMsg.value = 'Failed to load PDF'
  }
  loading.value = false
  resetHideTimer()
}

async function renderPage(num: number) {
  if (!pdfDoc || !canvas.value) return
  const page = await pdfDoc.getPage(num)
  const canvasEl = canvas.value
  const ctx = canvasEl.getContext('2d')
  if (!ctx) return

  const viewport = page.getViewport({scale: 1})
  const scaleX = window.innerWidth / viewport.width
  const scaleY = window.innerHeight / viewport.height
  const scale = Math.min(scaleX, scaleY) * canvasScale.value
  const scaledViewport = page.getViewport({scale})

  canvasEl.width = scaledViewport.width
  canvasEl.height = scaledViewport.height
  ctx.clearRect(0, 0, canvasEl.width, canvasEl.height)
  await page.render({canvasContext: ctx, viewport: scaledViewport}).promise
  currentPage.value = num
}

async function goToPage(num: number) {
  if (num < 1 || num > totalPages.value) return
  await renderPage(num)
}
function nextPage() { goToPage(currentPage.value + 1) }
function prevPage() { goToPage(currentPage.value - 1) }

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') emit('close')
  else if (e.key === 'ArrowRight' || e.key === 'ArrowDown' || e.key === ' ') { e.preventDefault(); nextPage() }
  else if (e.key === 'ArrowLeft' || e.key === 'ArrowUp') { e.preventDefault(); prevPage() }
}
function handleClick(e: MouseEvent) {
  if ((e.target as HTMLElement).tagName === 'CANVAS') {
    if (e.clientX / window.innerWidth > 0.5) nextPage()
    else prevPage()
  }
}
function handleMouseMove() { showControls() }

let touchStartX = 0
function handleTouchStart(e: TouchEvent) { touchStartX = e.touches[0].clientX; showControls() }
function handleTouchEnd(e: TouchEvent) {
  const dx = e.changedTouches[0].clientX - touchStartX
  if (Math.abs(dx) > 50) { dx < 0 ? nextPage() : prevPage() }
}

onMounted(() => {
  document.addEventListener('keydown', handleKeydown)
  nextTick(loadPdf)
})
onUnmounted(() => {
  document.removeEventListener('keydown', handleKeydown)
  if (hideTimeout) clearTimeout(hideTimeout)
  pdfDoc?.destroy()
})
</script>

<template>
  <Teleport to="body">
    <div
        class="fixed inset-0 z-[9999] bg-black flex flex-col items-center justify-center cursor-none"
        :class="{'!cursor-default': controlsVisible}"
        @click="handleClick"
        @mousemove="handleMouseMove"
        @touchstart="handleTouchStart"
        @touchend="handleTouchEnd"
    >
      <!-- Top bar -->
      <div
          class="absolute top-0 left-0 right-0 flex items-center justify-between px-4 py-2 bg-black/60 text-white z-10 transition-opacity duration-300"
          :class="controlsVisible ? 'opacity-100' : 'opacity-0 pointer-events-none'"
      >
        <span class="text-sm truncate">{{ title }}</span>
        <div class="flex items-center gap-4">
          <span v-if="totalPages > 0" class="text-sm">{{ currentPage }} / {{ totalPages }}</span>
          <IconButton :icon="['fas', 'xmark']" :label="t('common.close')" class="!text-white" @click.stop="emit('close')"/>
        </div>
      </div>

      <!-- Content -->
      <Spinner v-if="loading" size="lg" class="text-white"/>
      <Alert v-else-if="errorMsg" variant="error" class="m-4">{{ errorMsg }}</Alert>
      <canvas v-show="!loading && !errorMsg" ref="canvas"
              class="transition-all duration-300"
              :class="controlsVisible ? 'max-w-[95vw] max-h-[90vh]' : 'max-w-[100vw] max-h-[100vh]'"
      />

      <!-- Navigation buttons -->
      <div
          v-if="!loading && totalPages > 1"
          class="absolute bottom-4 flex items-center gap-4 transition-opacity duration-300"
          :class="controlsVisible ? 'opacity-100' : 'opacity-0 pointer-events-none'"
      >
        <IconButton
            :icon="['fas', 'chevron-left']" :label="t('common.previous')"
            class="!text-white !bg-white/20 !p-3 rounded-full" :disabled="currentPage <= 1"
            @click.stop="prevPage"
        />
        <IconButton
            :icon="['fas', 'chevron-right']" :label="t('common.next')"
            class="!text-white !bg-white/20 !p-3 rounded-full" :disabled="currentPage >= totalPages"
            @click.stop="nextPage"
        />
      </div>
    </div>
  </Teleport>
</template>
