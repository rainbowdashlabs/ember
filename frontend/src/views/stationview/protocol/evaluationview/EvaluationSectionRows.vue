/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import type { EvaluationResponse, TestProtocolSection } from '@/api/generated/schema'

/** A top-level section's row in the evaluation, followed by one row for every section under it. */
defineProps<{
  section: TestProtocolSection
  /** Every section under it, depth first, with how deep each sits. */
  descendants: { section: TestProtocolSection, depth: number }[]
  members: EvaluationResponse['members']
  sectionMaxPoints: (id: number) => number
  memberSectionScore: (memberId: number, sectionId: number) => number
  sectionAverage: (id: number) => string
  scoreColor: (score: number, max: number) => string
}>()
</script>

<template>
  <tr class="font-semibold">
    <td class="sticky left-0 z-10 bg-[var(--bg)]  border border-[var(--border)] px-2 py-1">{{ section.name }}</td>
    <td class="bg-[var(--bg)] border border-[var(--border)] px-2 py-1 text-center font-mono">{{ sectionMaxPoints(section.id) }}</td>
    <td :class="['border border-[var(--border)] px-2 py-1 text-center font-mono', scoreColor(parseFloat(sectionAverage(section.id)), sectionMaxPoints(section.id))]">{{ sectionAverage(section.id) }}</td>
    <td
      v-for="m in members"
      :key="m.memberId"
      :class="['border border-[var(--border)] px-2 py-1 text-center font-mono', scoreColor(memberSectionScore(m.memberId, section.id), sectionMaxPoints(section.id))]"
    >
      {{ memberSectionScore(m.memberId, section.id) }}
    </td>
  </tr>
  <tr v-for="{section: sub, depth} in descendants" :key="sub.id" class="text-[var(--text-muted)]">
    <td class="sticky left-0 z-10 bg-[var(--bg)]  border border-[var(--border)] px-2 py-1"
        :style="{paddingLeft: `${0.5 + depth}rem`}">{{ sub.name }}</td>
    <td class="bg-[var(--bg)] border border-[var(--border)] px-2 py-1 text-center font-mono">{{ sectionMaxPoints(sub.id) }}</td>
    <td :class="['border border-[var(--border)] px-2 py-1 text-center font-mono', scoreColor(parseFloat(sectionAverage(sub.id)), sectionMaxPoints(sub.id))]">{{ sectionAverage(sub.id) }}</td>
    <td
      v-for="m in members"
      :key="m.memberId"
      :class="['border border-[var(--border)] px-2 py-1 text-center font-mono', scoreColor(memberSectionScore(m.memberId, sub.id), sectionMaxPoints(sub.id))]"
    >
      {{ memberSectionScore(m.memberId, sub.id) }}
    </td>
  </tr>
</template>
