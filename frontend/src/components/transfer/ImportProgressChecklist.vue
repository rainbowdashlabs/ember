/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, nextTick, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Alert from '@/components/feedback/Alert.vue'
import {importPhaseName} from '@/util/importPhaseName'

interface ImportProgressShape {
  stationName: string
  status: 'IN_PROGRESS' | 'COMPLETED' | 'FAILED'
  phases: string[]
  completedPhases: number
  currentPhase: string | null
  subTotal: number
  subCompleted: number
  error: string | null
}

const props = defineProps<{
  progress: ImportProgressShape
}>()

const {t} = useI18n()

const VISIBLE_PENDING = 5

const total = computed(() => Math.max(1, props.progress.phases.length))
const percent = computed(() => Math.round((props.progress.completedPhases / total.value) * 100))

const subPercent = computed(() => {
  if (props.progress.subTotal <= 0) return 0
  return Math.min(100, Math.round((props.progress.subCompleted / props.progress.subTotal) * 100))
})

const currentIndex = computed(() => {
  if (!props.progress.currentPhase) return -1
  return props.progress.phases.indexOf(props.progress.currentPhase)
})

const visiblePhases = computed(() => {
  const phases = props.progress.phases
  const done = props.progress.completedPhases
  const current = currentIndex.value
  const stopAfter = Math.max(done, current + 1) + VISIBLE_PENDING
  return phases.slice(0, Math.min(stopAfter, phases.length))
})

const hiddenCount = computed(() => props.progress.phases.length - visiblePhases.value.length)

const listEl = ref<HTMLUListElement | null>(null)
const itemRefs = ref<(HTMLLIElement | null)[]>([])

function phaseStatus(index: number): 'done' | 'current' | 'pending' {
  if (props.progress.status === 'COMPLETED') return 'done'
  if (index < props.progress.completedPhases) return 'done'
  const isCurrent = props.progress.phases[index] === props.progress.currentPhase
  if (isCurrent) return 'current'
  return 'pending'
}

function setItemRef(el: Element | null, index: number) {
  itemRefs.value[index] = el as HTMLLIElement | null
}

watch(
    () => props.progress.currentPhase,
    async (phase) => {
      if (!phase) return
      await nextTick()
      const list = listEl.value
      if (!list) return
      const idx = props.progress.phases.indexOf(phase)
      const current = itemRefs.value[idx]
      if (!current) return
      const lookahead = itemRefs.value[Math.min(idx + 2, itemRefs.value.length - 1)]
      const target = lookahead ?? current
      const targetBottom = target.offsetTop + target.offsetHeight
      if (targetBottom > list.scrollTop + list.clientHeight) {
        list.scrollTo({top: targetBottom - list.clientHeight, behavior: 'smooth'})
      } else if (current.offsetTop < list.scrollTop) {
        list.scrollTo({top: current.offsetTop, behavior: 'smooth'})
      }
    },
    {immediate: true},
)
</script>

<template>
  <NeutralContainer class="space-y-4">
    <div class="space-y-2">
      <div class="flex items-center justify-between">
        <span class="text-sm font-medium">{{ progress.stationName }}</span>
        <span class="text-sm text-(--text-muted)">
          {{ progress.completedPhases }} / {{ progress.phases.length }} ({{ percent }}%)
        </span>
      </div>
      <div class="w-full bg-bg-light-accent dark:bg-bg-dark-accent rounded-full h-3 overflow-hidden">
        <div
            class="h-full rounded-full transition-all duration-300"
            :class="progress.status === 'FAILED' ? 'bg-error' : 'bg-primary'"
            :style="{ width: `${percent}%` }"
        />
      </div>
    </div>

    <ul ref="listEl" class="grid gap-1 text-sm max-h-72 overflow-y-auto pr-1 scroll-smooth">
      <template v-for="(phase, index) in visiblePhases" :key="phase">
        <li :ref="(el) => setItemRef(el as Element | null, index)"
            class="flex items-center gap-2"
            :class="{
              'text-(--text-muted)': phaseStatus(index) === 'pending',
              'text-(--text)': phaseStatus(index) === 'done',
              'font-medium text-primary': phaseStatus(index) === 'current',
            }">
          <font-awesome-icon
              v-if="phaseStatus(index) === 'done'"
              :icon="['fas', 'circle-check']"
              class="text-success"/>
          <font-awesome-icon
              v-else-if="phaseStatus(index) === 'current'"
              :icon="['fas', 'spinner']"
              class="animate-spin text-primary"/>
          <font-awesome-icon
              v-else
              :icon="['fas', 'circle']"
              class="text-(--text-muted) opacity-40"/>
          <span class="flex-1">{{ importPhaseName(phase) }}</span>
          <span v-if="phaseStatus(index) === 'current' && progress.subTotal > 0"
                class="text-xs text-(--text-muted) tabular-nums">
            {{ progress.subCompleted }} / {{ progress.subTotal }}
          </span>
        </li>
        <li v-if="phaseStatus(index) === 'current' && progress.subTotal > 0"
            class="-mt-1 pl-6 pr-1">
          <div class="w-full bg-bg-light-accent dark:bg-bg-dark-accent rounded-full h-1.5 overflow-hidden">
            <div class="h-full bg-primary rounded-full transition-all duration-300"
                 :style="{ width: `${subPercent}%` }"/>
          </div>
        </li>
      </template>
      <li v-if="hiddenCount > 0"
          class="flex items-center gap-2 text-xs text-(--text-muted) italic pt-1">
        <font-awesome-icon :icon="['fas', 'ellipsis']" class="opacity-50"/>
        <span>{{ t('transferImport.morePending', {count: hiddenCount}) }}</span>
      </li>
    </ul>

    <Alert v-if="progress.status === 'COMPLETED'" variant="success">
      {{ t('transferImport.completed') }}
    </Alert>
    <Alert v-if="progress.status === 'FAILED'" variant="error">
      {{ t('transferImport.failed', {error: progress.error ?? ''}) }}
    </Alert>
  </NeutralContainer>
</template>
