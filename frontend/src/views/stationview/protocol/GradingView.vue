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
import ButtonRow from '@/components/button/ButtonRow.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import { describeFailure } from '@/util/failure'
import GradingSectionPanel from './gradingview/GradingSectionPanel.vue'
import { useSession } from '@/composables/useSession'
import { useAsyncAction } from '@/composables/useAsyncAction'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import { protocol, stationMembers } from '@/api'
import type { GradingScope, MemberWithName, TestProtocolSection, TestProtocolItem } from '@/api/generated/schema'
import { reportCaughtError } from '@/util/devErrorReporter'
import { maxPointsOf, scoreOf } from './protocolPoints'
import { protocolTree } from './protocolTree'
import Alert from '@/components/feedback/Alert.vue'

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
const member = ref<MemberWithName | null>(null)
const locked = ref(false)
const currentSectionIndex = ref(0)

/**
 * Whose sheet is being marked, with the part after the name, because a run is graded member by
 * member and the run's name would be the same word above every one of them.
 */
const pageTitle = computed(() => {
  const name = member.value?.name || member.value?.email
  return name ? t('pages.protocol-grade.titleNamed', {name}) : t('pages.protocol-grade.title')
})

const scope = ref<GradingScope | null>(null)
const tree = computed(() => protocolTree(sections.value, items.value))

/** The sections whose points this examiner may change: every one, unless the run was planned. */
const gradable = computed<ReadonlySet<number>>(() => scope.value?.restricted
  ? new Set(scope.value.sectionIds)
  : new Set(sections.value.map(section => section.id)))

/** The top-level sections holding anything this examiner may grade, which are the steps of the sheet. */
const topSections = computed(() => tree.value.childrenOf(null)
  .filter(section => [...tree.value.subtreeOf(section.id)].some(id => gradable.value.has(id))))
const currentSection = computed(() => topSections.value[currentSectionIndex.value])

/** The steps of this examiner not marked finished yet, which is what is easy to forget. */
const openSections = computed(() => topSections.value.filter(section => !doneSections.value.has(section.id)))

/**
 * Whether every top-level section of the sheet is marked as checked, other examiners' included,
 * which is what finishes the examination.
 */
const everySectionDone = computed(() => tree.value.childrenOf(null).every(section => doneSections.value.has(section.id)))

const isChecked = (itemId: number) => checks.value.get(itemId) === true

function sectionCheckedScore(sectionId: number): number {
  return scoreOf(tree.value.itemsUnder(sectionId), isChecked)
}

function sectionMaxScore(sectionId: number): number {
  return tree.value.maxPointsUnder(sectionId)
}

const currentSectionScore = computed(() => (currentSection.value ? sectionCheckedScore(currentSection.value.id) : 0))

const currentSectionMaxPoints = computed(() => (currentSection.value ? sectionMaxScore(currentSection.value.id) : 0))

const totalScore = computed(() => scoreOf(items.value, isChecked))

const totalMaxPoints = computed(() => maxPointsOf(items.value))

let saveDebounce: ReturnType<typeof setTimeout> | null = null

function toggleCheck(itemId: number) {
  checks.value.set(itemId, !checks.value.get(itemId))
  if (saveDebounce) clearTimeout(saveDebounce)
  saveDebounce = setTimeout(() => autoSave(), 500)
}

function serializeChecks(): Record<number, boolean> {
  const checksObj: Record<number, boolean> = {}
  for (const [k, v] of checks.value) checksObj[k] = v
  return checksObj
}

async function autoSave() {
  try { await protocol.saveChecks(runId.value, memberId.value, serializeChecks()) }
  catch (e) { reportCaughtError(e, 'grading autosave') }
}

const {loading, failure, reload: loadData} = useAsyncLoader(async () => {
  await protocol.lockMember(runId.value, memberId.value)
  locked.value = true
  storedRunId = runId.value
  storedMemberId = memberId.value

  const [protocolData, existingChecks, doneIds, allMembers, gradingScope] = await Promise.all([
    protocol.getProtocol((await protocol.getRun(runId.value)).run.protocolId),
    protocol.getChecks(runId.value, memberId.value),
    protocol.getSectionsDone(runId.value, memberId.value),
    stationMembers.listMembers(),
    protocol.getGradingScope(runId.value),
  ])
  scope.value = gradingScope
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

/**
 * Writes the ticks of this section, then does whatever the button asked for next.
 *
 * <p>The two are answered for separately. Marking somebody complete, releasing them again and
 * moving on all used to share the save's `try`, so a grading the server had already taken, followed
 * by a step that was refused, said the grading had not been saved. A grader told that ticks the
 * whole section again.
 */
const {running: saving, failure: saveFailure, run: runSave} = useAsyncAction(
  async (after: () => void | Promise<void>) => {
    failure.value = null
    await protocol.saveChecks(runId.value, memberId.value, serializeChecks())
    try {
      await after()
    } catch (e) {
      failure.value = {...describeFailure(e, t), message: t('protocol.savedButNotFinished')}
    }
  })

function goNextSection() {
  if (currentSectionIndex.value < topSections.value.length - 1) {
    currentSectionIndex.value++
  }
}

function saveAndNext() {
  return runSave(goNextSection)
}

function savePrev() {
  return runSave(() => {
    if (currentSectionIndex.value > 0) currentSectionIndex.value--
  })
}

function backToRun() {
  return router.push({ name: 'protocol-run-detail', params: { id: runId.value } })
}

/**
 * Marks a section as checked or takes the mark back, always after the ticks were saved: the mark
 * that leaves no section open finishes the examination on the server, scored from what is stored.
 *
 * @returns whether this mark finished the examination
 */
async function markSection(sectionId: number): Promise<boolean> {
  doneSections.value = new Set(await protocol.toggleSectionDone(runId.value, memberId.value, sectionId))
  return everySectionDone.value
}

function toggleSectionDone(sectionId: number) {
  return runSave(async () => {
    if (await markSection(sectionId)) await backToRun()
  })
}

function markDoneAndNext() {
  return runSave(async () => {
    const section = currentSection.value
    if (section && !doneSections.value.has(section.id) && await markSection(section.id)) {
      await backToRun()
      return
    }
    goNextSection()
  })
}

function markDoneAndExit() {
  return runSave(async () => {
    const section = currentSection.value
    if (section && !doneSections.value.has(section.id)) await markSection(section.id)
    await protocol.unlockMember(runId.value, memberId.value)
    await backToRun()
  })
}

function saveAndExit() {
  return runSave(async () => {
    await protocol.unlockMember(runId.value, memberId.value)
    await backToRun()
  })
}

onBeforeUnmount(async () => {
  if (locked.value && storedRunId && storedMemberId) {
    try { await protocol.unlockMember(storedRunId, storedMemberId) } catch (e) { reportCaughtError(e, 'grading member unlock') }
  }
})

watch(loaded, (v) => { if (v) loadData() }, { immediate: true })
</script>

<template>
  <ViewContent
      :title="pageTitle"
      :subtitle="t('pages.protocol-grade.subtitle')"
  >
    <Spinner v-if="loading" size="lg" />
    <FailureAlert :failure="failure ?? saveFailure" class="mb-4"/>

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
          <font-awesome-icon v-if="doneSections.has(sec.id)" :icon="['fas', 'circle-check']" class="w-3 h-3 text-[var(--color-success)] mr-1" />
          {{ sec.name }}
          <span class="ml-1 font-mono">{{ sectionCheckedScore(sec.id) }}/{{ sectionMaxScore(sec.id) }}</span>
        </SelectionToggleButton>
      </div>

      <div class="flex items-center justify-between text-sm mb-4">
        <span class="text-[var(--text-muted)]">{{ t('protocol.totalScore') }}:</span>
        <span class="font-mono font-bold text-lg">{{ totalScore }} / {{ totalMaxPoints }}P</span>
      </div>

      <Alert v-if="openSections.length > 0" variant="info" class="mb-4">
        {{ t('protocol.openSections', {names: openSections.map(section => section.name).join(', ')}) }}
      </Alert>

      <GradingSectionPanel
        :section="currentSection"
        :tree="tree"
        :gradable="gradable"
        :checks="checks"
        :score="currentSectionScore"
        :max-points="currentSectionMaxPoints"
        :done="doneSections.has(currentSection.id)"
        @toggle-check="toggleCheck"
        @toggle-done="toggleSectionDone(currentSection.id)"
      />

      <div class="space-y-2">
        <ButtonRow>
          <SuccessButton v-if="!doneSections.has(currentSection.id) && currentSectionIndex < topSections.length - 1" class="sm:flex-initial" :disabled="saving" @click="markDoneAndNext">
            <font-awesome-icon :icon="['fas', 'check']" class="mr-1" /> {{ t('protocol.markDoneAndNext') }}
          </SuccessButton>
          <SuccessButton v-if="!doneSections.has(currentSection.id)" class="sm:flex-initial" :disabled="saving" @click="markDoneAndExit">
            <font-awesome-icon :icon="['fas', 'check']" class="mr-1" /> {{ t('protocol.markDoneAndExit') }}
          </SuccessButton>
        </ButtonRow>
        <ButtonRow>
          <SecondaryButton v-if="currentSectionIndex > 0" class="flex-1 sm:flex-initial" :disabled="saving" @click="savePrev">
            <font-awesome-icon :icon="['fas', 'chevron-left']" class="mr-1" /> {{ t('protocol.prevSection') }}
          </SecondaryButton>
          <div class="hidden sm:block flex-1" />
          <PrimaryButton v-if="currentSectionIndex < topSections.length - 1" class="sm:flex-initial" :disabled="saving" @click="saveAndNext">
            {{ t('protocol.nextSection') }} <font-awesome-icon :icon="['fas', 'chevron-right']" class="ml-1" />
          </PrimaryButton>
        </ButtonRow>
      </div>
    </template>
  </ViewContent>
</template>
