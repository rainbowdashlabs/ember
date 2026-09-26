/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, onMounted, onUnmounted} from 'vue'
import {useI18n} from 'vue-i18n'
import Spinner from '@/components/feedback/Spinner.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import PdfCanvas from '@/components/documents/PdfCanvas.vue'
import PresentationTopBar from './presentationviewer/PresentationTopBar.vue'
import PresentationPager from './presentationviewer/PresentationPager.vue'
import client from '@/api/client'
import {describeFailure, FailureKind, type Failure} from '@/util/failure'

const {t} = useI18n()
const props = defineProps<{
  contentUrl: string
  title: string
}>()
const emit = defineEmits<{ close: [] }>()

const source = ref<ArrayBuffer | null>(null)
const currentPage = ref(1)
const totalPages = ref(0)
const loading = ref(true)
const failure = ref<Failure | null>(null)
const controlsVisible = ref(true)
let hideTimeout: ReturnType<typeof setTimeout> | null = null

function showControls() {
  controlsVisible.value = true
  resetHideTimer()
}
function resetHideTimer() {
  if (hideTimeout) clearTimeout(hideTimeout)
  hideTimeout = setTimeout(() => { controlsVisible.value = false }, 2500)
}


async function fetchDocument() {
  loading.value = true
  failure.value = null
  try {
    const res = await client.get<ArrayBuffer>(props.contentUrl, {responseType: 'arraybuffer'})
    source.value = res.data
  } catch (e) {
    failure.value = {...describeFailure(e, t), message: t('kb.presentationLoadFailed')}
    loading.value = false
    resetHideTimer()
  }
}

function opened(pageCount: number) {
  totalPages.value = pageCount
  loading.value = false
  resetHideTimer()
}

/**
 * The document arrived and the viewer could not draw it, which is a different problem from not
 * getting it at all: nothing about the connection or the reader's rights is wrong, and trying again
 * will do exactly the same thing. Downloading the file and opening it elsewhere is the way out.
 */
function failed() {
  failure.value = {
    kind: FailureKind.UNKNOWN,
    message: t('kb.presentationRenderFailed'),
    guidance: t('kb.presentationRenderFailedGuidance'),
    reportable: true,
  }
  loading.value = false
  resetHideTimer()
}

function goToPage(num: number) {
  if (num < 1 || num > totalPages.value) return
  currentPage.value = num
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
function handleTouchStart(e: TouchEvent) { touchStartX = e.touches[0]?.clientX ?? 0; showControls() }
function handleTouchEnd(e: TouchEvent) {
  const endX = e.changedTouches[0]?.clientX
  if (endX === undefined) return
  const dx = endX - touchStartX
  if (Math.abs(dx) > 50) { dx < 0 ? nextPage() : prevPage() }
}

onMounted(() => {
  document.addEventListener('keydown', handleKeydown)
  fetchDocument()
})
onUnmounted(() => {
  document.removeEventListener('keydown', handleKeydown)
  if (hideTimeout) clearTimeout(hideTimeout)
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
      <PresentationTopBar
          :title="title"
          :current-page="currentPage"
          :total-pages="totalPages"
          :visible="controlsVisible"
          @close="emit('close')"
      />

      <Spinner v-if="loading" size="lg" class="text-white"/>
      <FailureAlert v-else-if="failure" :failure="failure" class="m-4"/>
      <PdfCanvas
          v-show="!loading && !failure"
          :source="source"
          :page="currentPage"
          class="transition-all duration-300"
          :class="controlsVisible ? 'max-w-[95vw] max-h-[90vh]' : 'max-w-[100vw] max-h-[100vh]'"
          @loaded="opened"
          @failed="failed"
      />

      <PresentationPager
          v-if="!loading && totalPages > 1"
          :current-page="currentPage"
          :total-pages="totalPages"
          :visible="controlsVisible"
          @previous="prevPage"
          @next="nextPage"
      />
    </div>
  </Teleport>
</template>
