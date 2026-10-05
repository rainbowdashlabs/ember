/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import type {TestProtocolSection} from '@/api/generated/schema'

/**
 * Every top-level section of the protocol for one member of a run, marked finished or still open, so
 * whoever runs the exam sees at a glance which parts are left and for whom.
 */
const props = defineProps<{
  sections: TestProtocolSection[]
  doneSectionIds: number[]
  /** Who examines each section, by name, where the run was planned. */
  examinerNames: (sectionId: number) => string[]
}>()

const {t} = useI18n()

function label(section: TestProtocolSection): string {
  const names = props.examinerNames(section.id)
  return names.length === 0 ? section.name : t('protocol.sectionWithExaminers', {name: section.name, names: names.join(', ')})
}
</script>

<template>
  <ul class="flex flex-wrap gap-1" :aria-label="t('protocol.sections')">
    <li v-for="section in sections" :key="section.id" :title="label(section)">
      <SuccessBadge v-if="doneSectionIds.includes(section.id)">{{ section.name }}</SuccessBadge>
      <SecondaryBadge v-else>{{ section.name }}</SecondaryBadge>
    </li>
  </ul>
</template>
