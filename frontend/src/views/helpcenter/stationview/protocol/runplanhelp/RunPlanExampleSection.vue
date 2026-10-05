/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import MutedText from '@/components/typography/MutedText.vue'
import PlanSectionNode from '@/views/stationview/protocol/runplanview/PlanSectionNode.vue'
import PlanUnassignedNotice from '@/views/stationview/protocol/runplanview/PlanUnassignedNotice.vue'
import { demoRunPlan } from '../fixtures'

/** The planning page for a run of the sample protocol, with examiners named on two of its three sections. */
const { t } = useI18n()
const plan = demoRunPlan(t)
</script>

<template>
  <HelpSection :title="t('helpCenter.protocolRunPlan.exampleTitle')">
    <div class="space-y-4">
      <MutedText tag="p" size="sm">{{ t('protocol.plan.hint') }}</MutedText>
      <ButtonRow>
        <SecondaryButton :icon="['fas', 'clone']" disabled>{{ t('protocol.plan.copyPrevious') }}</SecondaryButton>
      </ButtonRow>
      <PlanUnassignedNotice :sections="plan.unassigned.value" />

      <PlanSectionNode v-for="section in plan.tree.value.childrenOf(null)" :key="section.id"
                       :section="section" :depth="0" :plan="plan" />

      <ButtonRow pair align="end">
        <SecondaryButton disabled>{{ t('protocol.plan.skip') }}</SecondaryButton>
        <PrimaryButton disabled>{{ t('protocol.plan.save') }}</PrimaryButton>
      </ButtonRow>
    </div>
  </HelpSection>
</template>
