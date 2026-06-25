/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import EvaluationMemberHeader from './EvaluationMemberHeader.vue'
import EvaluationSectionRows from './EvaluationSectionRows.vue'
import EvaluationTotalRow from './EvaluationTotalRow.vue'
import type { EvaluationResponse, TestProtocolSection } from '@/api/protocol'
import type { StationMember } from '@/api/types'

const props = defineProps<{
  evalData: EvaluationResponse
  memberMap: Map<number, StationMember>
}>()

const emit = defineEmits<{
  (e: 'memberPdf', memberId: number): void
}>()

const { t } = useI18n()

function memberName(memberId: number): string {
  const m = props.memberMap.get(memberId)
  if (!m) return `#${memberId}`
  return m.name || m.email || `#${m.id}`
}

function topSections(): TestProtocolSection[] {
  return props.evalData.sections.filter(s => !s.parentId).sort((a, b) => a.position - b.position)
}

function childSections(parentId: number): TestProtocolSection[] {
  return props.evalData.sections.filter(s => s.parentId === parentId).sort((a, b) => a.position - b.position)
}

function sectionMaxPoints(sectionId: number): number {
  const direct = props.evalData.sectionMaxPoints[sectionId] ?? 0
  const childMax = childSections(sectionId).reduce((sum, c) => sum + sectionMaxPoints(c.id), 0)
  return direct + childMax
}

function memberSectionScore(memberId: number, sectionId: number): number {
  const member = props.evalData.members.find(m => m.memberId === memberId)
  if (!member) return 0
  const direct = member.sectionScores[sectionId] ?? 0
  const childScore = childSections(sectionId).reduce((sum, c) => sum + memberSectionScore(memberId, c.id), 0)
  return direct + childScore
}

function sectionAverage(sectionId: number): string {
  if (props.evalData.members.length === 0) return '0'
  const sum = props.evalData.members.reduce((s, m) => s + memberSectionScore(m.memberId, sectionId), 0)
  return (sum / props.evalData.members.length).toFixed(1)
}

const totalMax = computed(() => topSections().reduce((s, sec) => s + sectionMaxPoints(sec.id), 0))

const totalAverage = computed(() => {
  if (props.evalData.members.length === 0) return '0'
  const sum = props.evalData.members.reduce((s, m) => s + m.totalScore, 0)
  return (sum / props.evalData.members.length).toFixed(1)
})

function scoreColor(score: number, max: number): string {
  if (max === 0) return ''
  const ratio = score / max
  if (ratio >= 0.9) return 'bg-green-100 dark:bg-green-900/30 text-green-800 dark:text-green-300'
  if (ratio >= 0.6) return 'bg-yellow-100 dark:bg-yellow-900/30 text-yellow-800 dark:text-yellow-300'
  if (ratio >= 0.3) return 'bg-orange-100 dark:bg-orange-900/30 text-orange-800 dark:text-orange-300'
  return 'bg-red-100 dark:bg-red-900/30 text-red-800 dark:text-red-300'
}
</script>

<template>
  <div class="overflow-x-auto">
    <table class="eval-table text-xs w-full" style="border-collapse: separate; border-spacing: 0">
      <thead>
        <tr>
          <th class="sticky left-0 z-10 bg-[var(--bg-accent)]  border border-[var(--border)] px-2 py-1.5 text-left min-w-48">{{ t('protocol.topic') }}</th>
          <th class="bg-[var(--bg-accent)] border border-[var(--border)] px-2 py-1.5 text-center min-w-12">{{ t('protocol.maxPts') }}</th>
          <th class="bg-[var(--bg-accent)] border border-[var(--border)] px-2 py-1.5 text-center min-w-12">{{ t('protocol.avg') }}</th>
          <EvaluationMemberHeader
            v-for="m in evalData.members"
            :key="m.memberId"
            :name="memberName(m.memberId)"
            @open-pdf="emit('memberPdf', m.memberId)"
          />
        </tr>
      </thead>
      <tbody>
        <EvaluationSectionRows
          v-for="section in topSections()"
          :key="section.id"
          :section="section"
          :child-sections="childSections(section.id)"
          :members="evalData.members"
          :section-max-points="sectionMaxPoints"
          :member-section-score="memberSectionScore"
          :section-average="sectionAverage"
          :score-color="scoreColor"
        />
        <EvaluationTotalRow
          :members="evalData.members"
          :total-max="totalMax"
          :total-average="totalAverage"
          :score-color="scoreColor"
        />
      </tbody>
    </table>
  </div>
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
