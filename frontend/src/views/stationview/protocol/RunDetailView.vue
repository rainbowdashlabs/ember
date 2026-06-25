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
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import { useSession } from '@/composables/useSession'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import { protocol, stationMembers } from '@/api'
import type { TestProtocolRun, RunMemberWithProgress } from '@/api/protocol'
import type { StationMember } from '@/api/types'
import FieldLabel from '@/components/typography/FieldLabel.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const { canManageProtocol, canTestProtocol, loaded } = useSession()


const runId = computed(() => Number(route.params.id))
const run = ref<TestProtocolRun | null>(null)
const runMembers = ref<RunMemberWithProgress[]>([])
const memberMap = ref<Map<number, StationMember>>(new Map())
const filterIncomplete = ref(false)

const {loading, error, reload: loadData} = useAsyncLoader(async () => {
  const [runData, allMembers] = await Promise.all([
    protocol.getRun(runId.value),
    stationMembers.listMembers(),
  ])
  run.value = runData.run
  runMembers.value = runData.members
  memberMap.value = new Map(allMembers.map(m => [m.id, m]))
}, {autoLoad: false})

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

async function handleClose() {
  if (!run.value) return
  try { await protocol.closeRun(run.value.id); await loadData() }
  catch { error.value = t('common.error') }
}

function startGrading(memberId: number) {
  router.push({ name: 'protocol-grade', params: { id: runId.value, memberId } })
}

watch(loaded, (v) => { if (v) loadData() }, { immediate: true })
</script>

<template>
  <ViewContent>
    <div class="flex items-center gap-2 mb-4">
      <SecondaryButton @click="router.push({ name: 'protocol-run-list' })">
        <font-awesome-icon :icon="['fas', 'chevron-left']" />
      </SecondaryButton>
      <SectionHeader>{{ run?.name ?? '' }}</SectionHeader>
      <SuccessBadge v-if="run?.status === 'CLOSED'">{{ t('protocol.closed') }}</SuccessBadge>
      <PrimaryBadge v-else-if="run">{{ t('protocol.open') }}</PrimaryBadge>
      <div class="flex gap-2 ml-auto">
        <PrimaryButton v-if="run?.status === 'OPEN' && canManageProtocol()" @click="handleClose">
          {{ t('protocol.closeRun') }}
        </PrimaryButton>
        <SecondaryButton @click="router.push({ name: 'protocol-evaluation', params: { id: runId } })">
          <font-awesome-icon :icon="['fas', 'chart-bar']" class="mr-1" /> {{ t('protocol.evaluation') }}
        </SecondaryButton>
      </div>
    </div>

    <Spinner v-if="loading" />
    <Alert v-if="error" variant="error">{{ error }}</Alert>

    <template v-if="!loading && run">
      <div class="flex items-center gap-2 mb-4">
        <p class="text-sm text-[var(--text-muted)]">{{ new Date(run.testDate).toLocaleDateString('de-DE') }}</p>
        <FieldLabel inline class="cursor-pointer ml-auto text-[var(--text-muted)]">
          <ToggleInput v-model="filterIncomplete" />
          {{ t('protocol.filterIncomplete') }}
        </FieldLabel>
      </div>

      <div class="space-y-2">
        <NeutralContainer v-for="rm in filteredMembers" :key="rm.member.id" class="flex items-center gap-2">
          <div class="flex-1 min-w-0">
            <div class="font-medium">{{ memberName(rm.member.memberId) }}</div>
            <div class="text-xs text-[var(--text-muted)] flex items-center gap-2">
              <span>{{ rm.sectionsDone }}/{{ rm.sectionsTotal }} {{ t('protocol.sections') }}</span>
              <template v-if="rm.member.lockedBy">
                <font-awesome-icon :icon="['fas', 'lock']" />
                {{ testerName(rm.member.lockedBy) }}
              </template>
            </div>
          </div>
          <span class="font-mono text-sm">{{ rm.member.totalScore }}P</span>
          <SuccessBadge v-if="rm.member.completed">{{ t('protocol.completed') }}</SuccessBadge>
          <ErrorBadge v-else-if="rm.member.lockedBy">{{ t('protocol.locked') }}</ErrorBadge>
          <SecondaryBadge v-else>{{ t('protocol.pending') }}</SecondaryBadge>
          <PrimaryButton
            v-if="run.status === 'OPEN' && canTestProtocol() && !rm.member.completed"
            class="!text-sm !py-1 !px-3"
            @click="startGrading(rm.member.memberId)"
          >
            <font-awesome-icon :icon="['fas', 'clipboard-check']" class="mr-1" />
            {{ t('protocol.grade') }}
          </PrimaryButton>
        </NeutralContainer>
      </div>
    </template>
  </ViewContent>
</template>
