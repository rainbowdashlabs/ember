/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import GradingSectionPanel from '@/views/stationview/protocol/gradingview/GradingSectionPanel.vue'
import { maxPointsOf, scoreOf } from '@/views/stationview/protocol/protocolPoints'
import { DEMO_SECTION, demoProtocolItems, demoProtocolTree } from '../fixtures'

/**
 * The grading sheet as an examiner of a planned run sees it: the knots are theirs, the hoses nobody's,
 * and the emergency call belongs to somebody else and is left out. Two points are already ticked.
 */
const { t } = useI18n()
const tree = demoProtocolTree(t)
const items = demoProtocolItems(t)
const checks = new Map(items.map(item => [item.id, [1, 2, 4, 6].includes(item.id)]))
const isChecked = (itemId: number) => checks.get(itemId) === true

const gradable: ReadonlySet<number> = new Set([...tree.subtreeOf(DEMO_SECTION.knots), DEMO_SECTION.hoses])
const steps = tree.childrenOf(null).filter(section => [...tree.subtreeOf(section.id)].some(id => gradable.has(id)))
const [current] = steps

const stepScore = (sectionId: number) => scoreOf(tree.itemsUnder(sectionId), isChecked)
const totalScore = scoreOf(items, isChecked)
const totalMaxPoints = maxPointsOf(items)
</script>

<template>
  <HelpSection :title="t('helpCenter.protocolGrading.exampleTitle')">
    <div class="flex items-center justify-between mb-2">
      <div />
      <SecondaryButton disabled>
        <font-awesome-icon :icon="['fas', 'xmark']" class="mr-1" /> {{ t('protocol.saveAndExit') }}
      </SecondaryButton>
    </div>

    <div class="flex flex-wrap gap-1.5 mb-4">
      <SelectionToggleButton v-for="(step, index) in steps" :key="step.id" :selected="index === 0" disabled>
        {{ step.name }}
        <span class="ml-1 font-mono">{{ stepScore(step.id) }}/{{ tree.maxPointsUnder(step.id) }}</span>
      </SelectionToggleButton>
    </div>

    <div class="flex items-center justify-between text-sm mb-4">
      <span class="text-[var(--text-muted)]">{{ t('protocol.totalScore') }}:</span>
      <span class="font-mono font-bold text-lg">{{ totalScore }} / {{ totalMaxPoints }}P</span>
    </div>

    <Alert variant="info" class="mb-4">
      {{ t('protocol.openSections', {names: steps.map(step => step.name).join(', ')}) }}
    </Alert>

    <GradingSectionPanel
      v-if="current"
      :section="current"
      :tree="tree"
      :gradable="gradable"
      :checks="checks"
      :score="stepScore(current.id)"
      :max-points="tree.maxPointsUnder(current.id)"
      :done="false"
    />
  </HelpSection>
</template>
