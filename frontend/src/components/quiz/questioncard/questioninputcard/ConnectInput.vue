/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed, ref, watch, nextTick, onMounted } from 'vue'
import ConnectLeftColumn from './ConnectLeftColumn.vue'
import ConnectRightColumn from './ConnectRightColumn.vue'

const props = defineProps<{
  config: Record<string, unknown>
  disabled: boolean
  connectPairs: Record<string, string>
  connectRightOrder?: number[]
}>()

const emit = defineEmits<{
  setConnectPair: [leftIndex: number, rightValue: string]
}>()

const connectLeftItems = computed<string[]>(() => {
  const pairs = props.config.pairs as { left: string; right: string }[] | undefined
  if (pairs) return pairs.map(p => p.left)
  return (props.config.leftItems as string[]) ?? []
})

const connectRightItems = computed<string[]>(() => {
  const pairs = props.config.pairs as { left: string; right: string }[] | undefined
  const raw = pairs ? pairs.map(p => p.right) : ((props.config.rightItems as string[]) ?? [])
  const order = props.connectRightOrder
  if (order && order.length === raw.length) return order.map(i => raw[i] ?? '')
  return raw
})

const connectContainer = ref<HTMLElement | null>(null)
const selectedLeftIdx = ref<number | null>(null)
const connectDragFrom = ref<number | null>(null)
const connectDragOver = ref<number | null>(null)

const reverseConnectMap = computed<Record<string, number>>(() => {
  const map: Record<string, number> = {}
  for (const [leftIdx, rightVal] of Object.entries(props.connectPairs)) {
    if (rightVal) map[rightVal] = Number(leftIdx)
  }
  return map
})

function isRightConnected(right: string): boolean {
  return right in reverseConnectMap.value
}

function connectPair(leftIdx: number, right: string) {
  const oldLeftIdx = reverseConnectMap.value[right]
  if (oldLeftIdx !== undefined && oldLeftIdx !== leftIdx) {
    emit('setConnectPair', oldLeftIdx, '')
  }
  emit('setConnectPair', leftIdx, right)
}

function onLeftClick(leftIdx: number) {
  if (props.disabled) return
  if (selectedLeftIdx.value === leftIdx) {
    selectedLeftIdx.value = null
  } else {
    selectedLeftIdx.value = leftIdx
  }
}

function onRightClick(rightIdx: number) {
  if (props.disabled) return
  const right = connectRightItems.value[rightIdx]
  if (right === undefined) return
  if (selectedLeftIdx.value !== null) {
    connectPair(selectedLeftIdx.value, right)
    selectedLeftIdx.value = null
  } else {
    const connectedLeftIdx = reverseConnectMap.value[right]
    if (connectedLeftIdx !== undefined) {
      emit('setConnectPair', connectedLeftIdx, '')
    }
  }
}

function onConnectDragStart(e: DragEvent, leftIdx: number) {
  if (props.disabled) return
  connectDragFrom.value = leftIdx
  if (e.dataTransfer) {
    e.dataTransfer.effectAllowed = 'link'
    e.dataTransfer.setData('text/plain', String(leftIdx))
  }
}

function onConnectDragOverRight(e: DragEvent, rightIdx: number) {
  if (connectDragFrom.value === null) return
  e.preventDefault()
  if (e.dataTransfer) e.dataTransfer.dropEffect = 'link'
  connectDragOver.value = rightIdx
}

function onConnectDropRight(e: DragEvent, rightIdx: number) {
  e.preventDefault()
  const right = connectRightItems.value[rightIdx]
  if (connectDragFrom.value !== null && right !== undefined) {
    connectPair(connectDragFrom.value, right)
  }
  connectDragFrom.value = null
  connectDragOver.value = null
}

function onConnectDragEnd() {
  connectDragFrom.value = null
  connectDragOver.value = null
  selectedLeftIdx.value = null
}

function removeConnection(leftIdx: number) {
  if (props.disabled) return
  emit('setConnectPair', leftIdx, '')
}

interface ConnectLine {
  x1: number
  y1: number
  x2: number
  y2: number
  leftIdx: number
}

function getLinePoints(): ConnectLine[] {
  if (!connectContainer.value) return []
  const container = connectContainer.value
  const containerRect = container.getBoundingClientRect()
  const lines: ConnectLine[] = []

  for (const [leftIdxStr, rightVal] of Object.entries(props.connectPairs)) {
    if (!rightVal) continue
    const leftIdx = Number(leftIdxStr)
    const rightIdx = connectRightItems.value.indexOf(rightVal)
    if (rightIdx < 0) continue

    const leftEl = container.querySelector(`[data-connect-left="${leftIdx}"]`)
    const rightEl = container.querySelector(`[data-connect-right="${rightIdx}"]`)
    if (!leftEl || !rightEl) continue

    const leftRect = leftEl.getBoundingClientRect()
    const rightRect = rightEl.getBoundingClientRect()

    lines.push({
      x1: leftRect.right - containerRect.left,
      y1: leftRect.top + leftRect.height / 2 - containerRect.top,
      x2: rightRect.left - containerRect.left,
      y2: rightRect.top + rightRect.height / 2 - containerRect.top,
      leftIdx,
    })
  }
  return lines
}

const connectLines = ref<ConnectLine[]>([])

function updateConnectLines() {
  requestAnimationFrame(() => {
    connectLines.value = getLinePoints()
  })
}

watch(() => props.connectPairs, () => nextTick(updateConnectLines), { deep: true })
onMounted(() => nextTick(updateConnectLines))
</script>

<template>
  <div ref="connectContainer" class="relative">
    <svg class="absolute inset-0 w-full h-full pointer-events-none z-10">
      <line
        v-for="line in connectLines"
        :key="line.leftIdx"
        :x1="line.x1" :y1="line.y1" :x2="line.x2" :y2="line.y2"
        class="stroke-primary"
        stroke-width="2"
        stroke-linecap="round"
      />
    </svg>

    <div class="flex justify-between gap-12">
      <ConnectLeftColumn
        :items="connectLeftItems"
        :disabled="disabled"
        :selected-left-idx="selectedLeftIdx"
        :connect-pairs="connectPairs"
        @left-click="onLeftClick"
        @drag-start="onConnectDragStart"
        @drag-end="onConnectDragEnd"
        @remove-connection="removeConnection"
      />
      <ConnectRightColumn
        :items="connectRightItems"
        :disabled="disabled"
        :selected-left-idx="selectedLeftIdx"
        :connect-drag-over="connectDragOver"
        :is-right-connected="isRightConnected"
        @right-click="onRightClick"
        @drag-over="onConnectDragOverRight"
        @drop="onConnectDropRight"
      />
    </div>
  </div>
</template>
