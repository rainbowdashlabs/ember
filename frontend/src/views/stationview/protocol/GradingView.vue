/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed, onBeforeUnmount, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import GradingSectionPanel from './gradingview/GradingSectionPanel.vue'
import { useSession } from '@/composables/useSession'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import { protocol, stationMembers } from '@/api'
import type { TestProtocolSection, TestProtocolItem } from '@/api/protocol'
import type { StationMember } from '@/api/types'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const { loaded } = useSession()


const runId = computed(() => Number(route.params.id))
const memberId = computed(() => Number(route.params.memberId))
let storedRunId = 0
let storedMemberId = 0

const sections = ref<TestProtocolSection[]>([])
const items = ref<TestProtocolItem[]>([])
const checks = ref<Map<number, boolean>>(new Map())
const doneSections = ref<Set<number>>(new Set())
const member = ref<StationMember | null>(null)
const saving = ref(false)
const locked = ref(false)
const currentSectionIndex = ref(0)

const topSections = computed(() => sections.value.filter(s => !s.parentId).sort((a, b) => a.position - b.position))
const currentSection = computed(() => topSections.value[currentSectionIndex.value])

function childSections(parentId: number) {
  return sections.value.filter(s => s.parentId === parentId).sort((a, b) => a.position - b.position)
}

function sectionItems(sectionId: number): TestProtocolItem[] {
  return items.value.filter(i => i.sectionId === sectionId).sort((a, b) => a.position - b.position)
}

function allCurrentItems(): TestProtocolItem[] {
  if (!currentSection.value) return []
  const result = [...sectionItems(currentSection.value.id)]
  for (const sub of childSections(currentSection.value.id)) {
    result.push(...sectionItems(sub.id))
  }
  return result
}

const currentSectionScore = computed(() => {
  return allCurrentItems().reduce((sum, item) => {
    return sum + (checks.value.get(item.id) ? item.points : 0)
  }, 0)
})

const currentSectionMaxPoints = computed(() => {
  return allCurrentItems().reduce((sum, item) => sum + item.points, 0)
})

function sectionCheckedScore(sectionId: number): number {
  const allItems = [...sectionItems(sectionId)]
  for (const sub of childSections(sectionId)) allItems.push(...sectionItems(sub.id))
  return allItems.reduce((sum, i) => sum + (checks.value.get(i.id) ? i.points : 0), 0)
}

function sectionMaxScore(sectionId: number): number {
  const allItems = [...sectionItems(sectionId)]
  for (const sub of childSections(sectionId)) allItems.push(...sectionItems(sub.id))
  return allItems.reduce((sum, i) => sum + i.points, 0)
}

const totalScore = computed(() => {
  let sum = 0
  for (const item of items.value) {
    if (checks.value.get(item.id)) sum += item.points
  }
  return sum
})

const totalMaxPoints = computed(() => items.value.reduce((sum, i) => sum + i.points, 0))

let saveDebounce: ReturnType<typeof setTimeout> | null = null

function toggleCheck(itemId: number) {
  checks.value.set(itemId, !checks.value.get(itemId))
  if (saveDebounce) clearTimeout(saveDebounce)
  saveDebounce = setTimeout(() => autoSave(), 500)
}

async function toggleSectionDone(sectionId: number) {
  doneSections.value = new Set(await protocol.toggleSectionDone(runId.value, memberId.value, sectionId))
}

async function autoSave() {
  const checksObj: Record<number, boolean> = {}
  for (const [k, v] of checks.value) checksObj[k] = v
  try { await protocol.saveChecks(runId.value, memberId.value, checksObj) }
  catch { }
}

const {loading, error, reload: loadData} = useAsyncLoader(async () => {
  await protocol.lockMember(runId.value, memberId.value)
  locked.value = true
  storedRunId = runId.value
  storedMemberId = memberId.value

  const [protocolData, existingChecks, doneIds, allMembers] = await Promise.all([
    protocol.getProtocol((await protocol.getRun(runId.value)).run.protocolId),
    protocol.getChecks(runId.value, memberId.value),
    protocol.getSectionsDone(runId.value, memberId.value),
    stationMembers.listMembers(),
  ])
  sections.value = protocolData.sections
  items.value = protocolData.items
  member.value = allMembers.find(m => m.id === memberId.value) ?? null

  const checkMap = new Map<number, boolean>()
  for (const item of protocolData.items) {
    checkMap.set(item.id, false)
  }
  for (const c of existingChecks) {
    checkMap.set(c.itemId, c.checked)
  }
  checks.value = checkMap
  doneSections.value = new Set(doneIds)
}, {autoLoad: false, errorMessageKey: 'protocol.lockError'})

async function saveAndNext() {
  saving.value = true
  try {
    const checksObj: Record<number, boolean> = {}
    for (const [k, v] of checks.value) checksObj[k] = v
    await protocol.saveChecks(runId.value, memberId.value, checksObj)

    if (currentSectionIndex.value < topSections.value.length - 1) {
      currentSectionIndex.value++
    }
  } catch { error.value = t('common.error') }
  finally { saving.value = false }
}

async function savePrev() {
  saving.value = true
  try {
    const checksObj: Record<number, boolean> = {}
    for (const [k, v] of checks.value) checksObj[k] = v
    await protocol.saveChecks(runId.value, memberId.value, checksObj)
    if (currentSectionIndex.value > 0) currentSectionIndex.value--
  } catch { error.value = t('common.error') }
  finally { saving.value = false }
}

async function finishGrading() {
  saving.value = true
  try {
    const checksObj: Record<number, boolean> = {}
    for (const [k, v] of checks.value) checksObj[k] = v
    await protocol.saveChecks(runId.value, memberId.value, checksObj)
    await protocol.completeMember(runId.value, memberId.value)
    router.push({ name: 'protocol-run-detail', params: { id: runId.value } })
  } catch { error.value = t('common.error') }
  finally { saving.value = false }
}

async function markDoneAndNext() {
  saving.value = true
  try {
    const checksObj: Record<number, boolean> = {}
    for (const [k, v] of checks.value) checksObj[k] = v
    await protocol.saveChecks(runId.value, memberId.value, checksObj)
    if (!doneSections.value.has(currentSection.value.id)) {
      doneSections.value = new Set(await protocol.toggleSectionDone(runId.value, memberId.value, currentSection.value.id))
    }
    if (currentSectionIndex.value < topSections.value.length - 1) {
      currentSectionIndex.value++
    }
  } catch { error.value = t('common.error') }
  finally { saving.value = false }
}

async function markDoneAndExit() {
  saving.value = true
  try {
    const checksObj: Record<number, boolean> = {}
    for (const [k, v] of checks.value) checksObj[k] = v
    await protocol.saveChecks(runId.value, memberId.value, checksObj)
    if (!doneSections.value.has(currentSection.value.id)) {
      await protocol.toggleSectionDone(runId.value, memberId.value, currentSection.value.id)
    }
    await protocol.unlockMember(runId.value, memberId.value)
    router.push({ name: 'protocol-run-detail', params: { id: runId.value } })
  } catch { error.value = t('common.error') }
  finally { saving.value = false }
}

async function saveAndExit() {
  saving.value = true
  try {
    const checksObj: Record<number, boolean> = {}
    for (const [k, v] of checks.value) checksObj[k] = v
    await protocol.saveChecks(runId.value, memberId.value, checksObj)
    await protocol.unlockMember(runId.value, memberId.value)
    router.push({ name: 'protocol-run-detail', params: { id: runId.value } })
  } catch { error.value = t('common.error') }
  finally { saving.value = false }
}

onBeforeUnmount(async () => {
  if (locked.value && storedRunId && storedMemberId) {
    try { await protocol.unlockMember(storedRunId, storedMemberId) } catch { }
  }
})

watch(loaded, (v) => { if (v) loadData() }, { immediate: true })
</script>

<template>
  <ViewContent
      :title="t('pages.protocol-grade.title')"
      :subtitle="t('pages.protocol-grade.subtitle')"
  >
    <Spinner v-if="loading" size="lg" />
    <Alert v-if="error" variant="error" class="mb-4">{{ error }}</Alert>

    <template v-if="!loading && currentSection">
      <div class="flex items-center justify-between mb-2">
        <div />
        <SecondaryButton @click="saveAndExit">
          <font-awesome-icon :icon="['fas', 'xmark']" class="mr-1" /> {{ t('protocol.saveAndExit') }}
        </SecondaryButton>
      </div>

      <div class="flex flex-wrap gap-1.5 mb-4">
        <SelectionToggleButton
          v-for="(sec, idx) in topSections"
          :key="sec.id"
          :selected="idx === currentSectionIndex"
          @toggle="currentSectionIndex = idx"
        >
          <font-awesome-icon v-if="doneSections.has(sec.id)" :icon="['fas', 'circle-check']" class="w-3 h-3 text-[var(--success)] mr-1" />
          {{ sec.name }}
          <span class="ml-1 font-mono">{{ sectionCheckedScore(sec.id) }}/{{ sectionMaxScore(sec.id) }}</span>
        </SelectionToggleButton>
      </div>

      <div class="flex items-center justify-between text-sm mb-4">
        <span class="text-[var(--text-muted)]">{{ t('protocol.totalScore') }}:</span>
        <span class="font-mono font-bold text-lg">{{ totalScore }} / {{ totalMaxPoints }}P</span>
      </div>

      <GradingSectionPanel
        :section="currentSection"
        :child-sections="childSections(currentSection.id)"
        :section-items="sectionItems"
        :checks="checks"
        :score="currentSectionScore"
        :max-points="currentSectionMaxPoints"
        :done="doneSections.has(currentSection.id)"
        @toggle-check="toggleCheck"
        @toggle-done="toggleSectionDone(currentSection.id)"
      />

      <div class="space-y-2">
        <div class="flex items-center gap-2">
          <SuccessButton v-if="!doneSections.has(currentSection.id) && currentSectionIndex < topSections.length - 1" class="flex-1 sm:flex-initial" @click="markDoneAndNext" :disabled="saving">
            <font-awesome-icon :icon="['fas', 'check']" class="mr-1" /> {{ t('protocol.markDoneAndNext') }}
          </SuccessButton>
          <SuccessButton v-if="!doneSections.has(currentSection.id)" class="flex-1 sm:flex-initial" @click="markDoneAndExit" :disabled="saving">
            <font-awesome-icon :icon="['fas', 'check']" class="mr-1" /> {{ t('protocol.markDoneAndExit') }}
          </SuccessButton>
        </div>
        <div class="flex items-center gap-2">
          <SecondaryButton v-if="currentSectionIndex > 0" class="flex-1 sm:flex-initial" @click="savePrev" :disabled="saving">
            <font-awesome-icon :icon="['fas', 'chevron-left']" class="mr-1" /> {{ t('protocol.prevSection') }}
          </SecondaryButton>
          <div class="hidden sm:block flex-1" />
          <SuccessButton v-if="currentSectionIndex === topSections.length - 1" class="flex-1 sm:flex-initial" @click="finishGrading" :disabled="saving">
            <font-awesome-icon :icon="['fas', 'flag']" class="mr-1" /> {{ t('protocol.finish') }}
          </SuccessButton>
          <PrimaryButton v-if="currentSectionIndex < topSections.length - 1" class="flex-1 sm:flex-initial" @click="saveAndNext" :disabled="saving">
            {{ t('protocol.nextSection') }} <font-awesome-icon :icon="['fas', 'chevron-right']" class="ml-1" />
          </PrimaryButton>
        </div>
      </div>
    </template>
  </ViewContent>
</template>
