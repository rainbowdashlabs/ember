/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {nextTick, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import type {TimelineItem} from '@/util/messageTimeline'
import DaySeparator from './DaySeparator.vue'
import UnreadLine from './UnreadLine.vue'
import MessageItem from './MessageItem.vue'
import type {MessageActions} from './messageActions'

/**
 * The scrolling lines of a conversation, newest at the bottom. It opens at the bottom, follows new
 * messages while the reader is there, and keeps its place while older ones are loaded above.
 */
const props = defineProps<{
  items: readonly TimelineItem[]
  hasOlder: boolean
  actions: MessageActions
  loadOlder: () => Promise<void>
}>()

const emit = defineEmits<{
  atBottom: [atBottom: boolean]
}>()

const {t} = useI18n()
const scroller = ref<HTMLElement | null>(null)
const atBottom = ref(true)

function nearBottom(element: HTMLElement): boolean {
  return element.scrollHeight - element.scrollTop - element.clientHeight < 48
}

function onScroll() {
  const element = scroller.value
  if (!element) return
  const now = nearBottom(element)
  if (now !== atBottom.value) {
    atBottom.value = now
    emit('atBottom', now)
  }
}

function toBottom() {
  const element = scroller.value
  if (element) element.scrollTop = element.scrollHeight
}

async function older() {
  const element = scroller.value
  const before = element?.scrollHeight ?? 0
  await props.loadOlder()
  await nextTick()
  if (element) element.scrollTop += element.scrollHeight - before
}

watch(() => props.items.length, async () => {
  if (!atBottom.value) return
  await nextTick()
  toBottom()
})

onMounted(async () => {
  await nextTick()
  toBottom()
  emit('atBottom', true)
})
</script>

<template>
  <div ref="scroller" class="flex-1 overflow-y-auto px-3 py-2" data-testid="message-timeline" @scroll.passive="onScroll">
    <div v-if="hasOlder" class="flex justify-center py-2">
      <SecondaryButton compact @click="older">{{ t('messages.loadOlder') }}</SecondaryButton>
    </div>
    <template v-for="item in items" :key="item.key">
      <DaySeparator v-if="item.kind === 'day'" :day="item.day"/>
      <UnreadLine v-else-if="item.kind === 'unread'"/>
      <MessageItem v-else :message="item.message" :opens-run="item.opensRun" :actions="actions"/>
    </template>
  </div>
</template>
