/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import { useSession } from '@/composables/useSession'
import { protocol, stationMembers } from '@/api'
import type { EvaluationResponse, TestProtocolSection } from '@/api/protocol'
import type { StationMember } from '@/api/types'
import MutedText from '@/components/typography/MutedText.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const { loaded } = useSession()


const runId = computed(() => Number(route.params.id))
const evalData = ref<EvaluationResponse | null>(null)
const memberMap = ref<Map<number, StationMember>>(new Map())
const loading = ref(true)
const error = ref('')

async function loadData() {
  loading.value = true
  try {
    const [ev, members] = await Promise.all([
      protocol.getEvaluation(runId.value),
      stationMembers.listMembers(),
    ])
    evalData.value = ev
    memberMap.value = new Map(members.map(m => [m.id, m]))
  } catch { error.value = t('common.error') }
  finally { loading.value = false }
}

function memberName(memberId: number): string {
  const m = memberMap.value.get(memberId)
  if (!m) return `#${memberId}`
  return m.name || m.email || `#${m.id}`
}

function topSections(): TestProtocolSection[] {
  return evalData.value?.sections.filter(s => !s.parentId).sort((a, b) => a.position - b.position) ?? []
}

function childSections(parentId: number): TestProtocolSection[] {
  return evalData.value?.sections.filter(s => s.parentId === parentId).sort((a, b) => a.position - b.position) ?? []
}

function sectionMaxPoints(sectionId: number): number {
  if (!evalData.value) return 0
  const direct = evalData.value.sectionMaxPoints[sectionId] ?? 0
  const childMax = childSections(sectionId).reduce((sum, c) => sum + sectionMaxPoints(c.id), 0)
  return direct + childMax
}

function memberSectionScore(memberId: number, sectionId: number): number {
  const member = evalData.value?.members.find(m => m.memberId === memberId)
  if (!member) return 0
  const direct = member.sectionScores[sectionId] ?? 0
  const childScore = childSections(sectionId).reduce((sum, c) => sum + memberSectionScore(memberId, c.id), 0)
  return direct + childScore
}

function sectionAverage(sectionId: number): string {
  if (!evalData.value || evalData.value.members.length === 0) return '0'
  const sum = evalData.value.members.reduce((s, m) => s + memberSectionScore(m.memberId, sectionId), 0)
  return (sum / evalData.value.members.length).toFixed(1)
}

const totalMax = computed(() => topSections().reduce((s, sec) => s + sectionMaxPoints(sec.id), 0))

const totalAverage = computed(() => {
  if (!evalData.value || evalData.value.members.length === 0) return '0'
  const sum = evalData.value.members.reduce((s, m) => s + m.totalScore, 0)
  return (sum / evalData.value.members.length).toFixed(1)
})

function scoreColor(score: number, max: number): string {
  if (max === 0) return ''
  const ratio = score / max
  if (ratio >= 0.9) return 'bg-green-100 dark:bg-green-900/30 text-green-800 dark:text-green-300'
  if (ratio >= 0.6) return 'bg-yellow-100 dark:bg-yellow-900/30 text-yellow-800 dark:text-yellow-300'
  if (ratio >= 0.3) return 'bg-orange-100 dark:bg-orange-900/30 text-orange-800 dark:text-orange-300'
  return 'bg-red-100 dark:bg-red-900/30 text-red-800 dark:text-red-300'
}

function exportAllZip() {
  return protocol.exportAllZip(runId.value)
}

function exportTablePdf() {
  return protocol.evaluationPdf(runId.value)
}

function openMemberPdf(memberId: number) {
  return protocol.exportMemberPdf(runId.value, memberId)
}

watch(loaded, (v) => { if (v) loadData() }, { immediate: true })
onMounted(() => { if (loaded.value) loadData() })
</script>

<template>
  <ViewContent>
    <div class="flex items-center gap-2 mb-4">
      <SecondaryButton @click="router.push({ name: 'protocol-run-detail', params: { id: runId } })">
        <font-awesome-icon :icon="['fas', 'chevron-left']" />
      </SecondaryButton>
      <SectionHeader>{{ t('protocol.evaluation') }}</SectionHeader>
      <div class="flex gap-2 ml-auto">
        <PrimaryButton @click="exportTablePdf">
          <font-awesome-icon :icon="['fas', 'file-pdf']" class="mr-1" /> {{ t('protocol.exportTable') }}
        </PrimaryButton>
        <SecondaryButton @click="exportAllZip">
          <font-awesome-icon :icon="['fas', 'download']" class="mr-1" /> {{ t('protocol.exportAll') }}
        </SecondaryButton>
      </div>
    </div>

    <Spinner v-if="loading" />
    <Alert v-if="error" variant="error">{{ error }}</Alert>

    <template v-if="!loading && evalData">
      <MutedText tag="p" size="sm">
        {{ evalData.protocolName }} — {{ new Date(evalData.testDate).toLocaleDateString('de-DE') }}
        <template v-if="evalData.passThreshold"> — {{ t('protocol.threshold') }}: {{ evalData.passThreshold }}P</template>
      </MutedText>

      <div class="overflow-x-auto">
        <table class="eval-table text-xs w-full" style="border-collapse: separate; border-spacing: 0">
          <thead>
            <tr>
              <th class="sticky left-0 z-10 bg-[var(--bg-accent)]  border border-[var(--border)] px-2 py-1.5 text-left min-w-48">{{ t('protocol.topic') }}</th>
              <th class="bg-[var(--bg-accent)] border border-[var(--border)] px-2 py-1.5 text-center min-w-12">{{ t('protocol.maxPts') }}</th>
              <th class="bg-[var(--bg-accent)] border border-[var(--border)] px-2 py-1.5 text-center min-w-12">{{ t('protocol.avg') }}</th>
              <th
                v-for="m in evalData.members"
                :key="m.memberId"
                class="bg-[var(--bg-accent)] border border-[var(--border)] px-2 py-1.5 text-center min-w-20 whitespace-nowrap"
              >
                <div class="font-medium mb-1">{{ memberName(m.memberId) }}</div>
                <PrimaryButton class="!text-xs !py-0.5 !px-2" @click="openMemberPdf(m.memberId)">
                  <font-awesome-icon :icon="['fas', 'file-pdf']" class="mr-1" /> PDF
                </PrimaryButton>
              </th>
            </tr>
          </thead>
          <tbody>
            <template v-for="section in topSections()" :key="section.id">
              <!-- Top-level section row -->
              <tr class="font-semibold">
                <td class="sticky left-0 z-10 bg-[var(--bg)]  border border-[var(--border)] px-2 py-1">{{ section.name }}</td>
                <td class="bg-[var(--bg)] border border-[var(--border)] px-2 py-1 text-center font-mono">{{ sectionMaxPoints(section.id) }}</td>
                <td :class="['border border-[var(--border)] px-2 py-1 text-center font-mono', scoreColor(parseFloat(sectionAverage(section.id)), sectionMaxPoints(section.id))]">{{ sectionAverage(section.id) }}</td>
                <td
                  v-for="m in evalData.members"
                  :key="m.memberId"
                  :class="['border border-[var(--border)] px-2 py-1 text-center font-mono', scoreColor(memberSectionScore(m.memberId, section.id), sectionMaxPoints(section.id))]"
                >
                  {{ memberSectionScore(m.memberId, section.id) }}
                </td>
              </tr>
              <!-- Subsection rows -->
              <tr v-for="sub in childSections(section.id)" :key="sub.id" class="text-[var(--text-muted)]">
                <td class="sticky left-0 z-10 bg-[var(--bg)]  border border-[var(--border)] px-2 py-1 pl-6">{{ sub.name }}</td>
                <td class="bg-[var(--bg)] border border-[var(--border)] px-2 py-1 text-center font-mono">{{ sectionMaxPoints(sub.id) }}</td>
                <td :class="['border border-[var(--border)] px-2 py-1 text-center font-mono', scoreColor(parseFloat(sectionAverage(sub.id)), sectionMaxPoints(sub.id))]">{{ sectionAverage(sub.id) }}</td>
                <td
                  v-for="m in evalData.members"
                  :key="m.memberId"
                  :class="['border border-[var(--border)] px-2 py-1 text-center font-mono', scoreColor(memberSectionScore(m.memberId, sub.id), sectionMaxPoints(sub.id))]"
                >
                  {{ memberSectionScore(m.memberId, sub.id) }}
                </td>
              </tr>
            </template>
            <!-- Total row -->
            <tr class="font-bold text-sm">
              <td class="sticky left-0 z-10 bg-[var(--bg-accent)]  border border-[var(--border)] px-2 py-1.5">{{ t('protocol.total') }}</td>
              <td class="bg-[var(--bg-accent)] border border-[var(--border)] px-2 py-1.5 text-center font-mono">{{ totalMax }}</td>
              <td :class="['border border-[var(--border)] px-2 py-1.5 text-center font-mono', scoreColor(parseFloat(totalAverage), totalMax)]">{{ totalAverage }}</td>
              <td
                v-for="m in evalData.members"
                :key="m.memberId"
                :class="['border border-[var(--border)] px-2 py-1.5 text-center font-mono', scoreColor(m.totalScore, totalMax)]"
              >
                {{ m.totalScore }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
  </ViewContent>
</template>

<style>
.eval-table td:nth-child(1),
.eval-table th:nth-child(1) {
  position: sticky;
  left: 0;
  z-index: 3;
}
.eval-table td:nth-child(2),
.eval-table th:nth-child(2) {
  position: sticky;
  left: 12rem;
  z-index: 2;
  background: var(--bg);
}
.eval-table td:nth-child(3),
.eval-table th:nth-child(3) {
  position: sticky;
  left: 15rem;
  z-index: 2;
  background: var(--bg);
}
.eval-table thead th:nth-child(2),
.eval-table thead th:nth-child(3),
.eval-table tr:last-child td:nth-child(2),
.eval-table tr:last-child td:nth-child(3) {
  background: var(--bg-accent);
}
.eval-table tr.font-semibold td:nth-child(2) {
  background: var(--bg);
}
</style>
