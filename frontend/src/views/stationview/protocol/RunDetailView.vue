/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import { useSession } from '@/composables/useSession'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import { describeFailure } from '@/util/failure'
import { protocol, stationMembers } from '@/api'
import {
  RunStatus,
  StationPermission,
  type GradingScope,
  type MemberWithName,
  type RunExaminers,
  type TestProtocolRun,
  type TestProtocolSection,
  type RunMemberWithProgress,
} from '@/api/generated/schema'
import RunMemberCard from './rundetailview/RunMemberCard.vue'
import { protocolTree } from './protocolTree'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import { formatDate } from '@/util/format'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const { canManageProtocol, canTestProtocol, hasPermission, loaded } = useSession()


const runId = computed(() => Number(route.params.id))
const run = ref<TestProtocolRun | null>(null)
const runMembers = ref<RunMemberWithProgress[]>([])
const memberMap = ref<Map<number, MemberWithName>>(new Map())
const filterIncomplete = ref(false)

const topSections = ref<TestProtocolSection[]>([])
const examiners = ref<RunExaminers>({sections: []})
const scope = ref<GradingScope | null>(null)

const {loading, failure, reload: loadData} = useAsyncLoader(async () => {
  const [runData, allMembers, plan, gradingScope] = await Promise.all([
    protocol.getRun(runId.value),
    stationMembers.listMembers(),
    protocol.getExaminers(runId.value),
    protocol.getGradingScope(runId.value),
  ])
  run.value = runData.run
  runMembers.value = runData.members
  memberMap.value = new Map(allMembers.map(m => [m.id, m]))
  examiners.value = plan
  scope.value = gradingScope
  const protocolData = await protocol.getProtocol(runData.run.protocolId)
  topSections.value = protocolTree(protocolData.sections, []).childrenOf(null)
}, {autoLoad: false})

const canPlan = computed(() => hasPermission(StationPermission.PROTOCOL_CREATE) && run.value?.status === RunStatus.OPEN)
const mayGrade = computed(() => canTestProtocol() && scope.value?.mayGrade === true)

function examinerNames(sectionId: number): string[] {
  return examiners.value.sections
    .filter(entry => entry.sectionId === sectionId)
    .flatMap(entry => entry.memberIds.map(memberName))
}

function memberName(memberId: number): string {
  const m = memberMap.value.get(memberId)
  if (!m) return `#${memberId}`
  return m.name || m.email || `#${m.id}`
}

function testerName(testerId: number | null): string {
  if (!testerId) return ''
  return memberName(testerId)
}

const filteredMembers = computed(() => {
  if (!filterIncomplete.value) return runMembers.value
  return runMembers.value.filter(rm => rm.sectionsDone < rm.sectionsTotal || !rm.member.completed)
})

/**
 * Closes the run, then reads it back.
 *
 * <p>Answered for separately: a run the server had already closed, followed by a page that would
 * not refresh, used to say the run would not close, and closing it again is not what anybody wants
 * to be invited to do.
 */
async function handleClose() {
  if (!run.value) return
  failure.value = null
  try {
    await protocol.closeRun(run.value.id)
  } catch (e) {
    failure.value = describeFailure(e, t)
    return
  }
  await loadData()
}

function startGrading(memberId: number) {
  router.push({ name: 'protocol-grade', params: { id: runId.value, memberId } })
}

/**
 * The run's own name at the head of the page, since a station holds many runs of the same protocol
 * and "Prüfungsdurchlauf" tells none of them apart in a tab or a bookmark.
 */
const pageTitle = computed(() => run.value?.name || t('pages.protocol-run-detail.title'))

watch(loaded, (v) => { if (v) loadData() }, { immediate: true })
</script>

<template>
  <ViewContent
      :title="pageTitle"
      :subtitle="t('pages.protocol-run-detail.subtitle')"
  >
    <div class="flex flex-wrap items-center gap-2 mb-4">
      <SecondaryButton @click="router.push({ name: 'protocol-run-list' })">
        <font-awesome-icon :icon="['fas', 'chevron-left']" />
      </SecondaryButton>
      <SectionHeader class="min-w-0 break-words">{{ run?.name ?? '' }}</SectionHeader>
      <SuccessBadge v-if="run?.status === RunStatus.CLOSED">{{ t('protocol.closed') }}</SuccessBadge>
      <PrimaryBadge v-else-if="run">{{ t('protocol.open') }}</PrimaryBadge>
      <ButtonRow align="end" class="w-full sm:w-auto sm:ml-auto">
        <PrimaryButton v-if="run?.status === RunStatus.OPEN && canManageProtocol()" @click="handleClose">
          {{ t('protocol.closeRun') }}
        </PrimaryButton>
        <SecondaryButton v-if="canPlan" :icon="['fas', 'user-check']"
                         @click="router.push({ name: 'protocol-run-plan', params: { id: runId } })">
          {{ t('protocol.plan.open') }}
        </SecondaryButton>
        <SecondaryButton @click="router.push({ name: 'protocol-evaluation', params: { id: runId } })">
          <font-awesome-icon :icon="['fas', 'chart-bar']" class="mr-1" /> {{ t('protocol.evaluation') }}
        </SecondaryButton>
      </ButtonRow>
    </div>

    <Spinner v-if="loading" />
    <FailureAlert :failure="failure"/>

    <template v-if="!loading && run">
      <div class="flex flex-wrap items-center gap-2 mb-4">
        <p class="text-sm text-[var(--text-muted)]">{{ formatDate(run.testDate) }}</p>
        <FieldLabel inline class="cursor-pointer ml-auto text-[var(--text-muted)]">
          <ToggleInput v-model="filterIncomplete" />
          {{ t('protocol.filterIncomplete') }}
        </FieldLabel>
      </div>

      <div class="space-y-2">
        <RunMemberCard
          v-for="rm in filteredMembers"
          :key="rm.member.id"
          :entry="rm"
          :name="memberName(rm.member.memberId)"
          :locked-by="testerName(rm.member.lockedBy)"
          :sections="topSections"
          :examiner-names="examinerNames"
          :can-grade="run.status === RunStatus.OPEN && mayGrade && !rm.member.completed"
          @grade="startGrading(rm.member.memberId)"
        />
      </div>
    </template>
  </ViewContent>
</template>
