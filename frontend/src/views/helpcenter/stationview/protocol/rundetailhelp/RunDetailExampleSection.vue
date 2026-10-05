/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import RunMemberSections from '@/views/stationview/protocol/rundetailview/RunMemberSections.vue'
import { DEMO_SECTION, demoExaminers, demoProtocolTree, demoRunMembers } from '../fixtures'

/** An open run of the sample protocol with three members in different states, planned with examiners. */
const { t } = useI18n()
const topSections = demoProtocolTree(t).childrenOf(null)
const members = demoRunMembers(t)
const [anna, tom, lisa] = demoExaminers(t)

const examinersBySection = new Map<number, string[]>([
  [DEMO_SECTION.emergencyCall, [anna?.name ?? '']],
  [DEMO_SECTION.knots, [tom?.name ?? '', lisa?.name ?? '']],
])

function examinerNames(sectionId: number): string[] {
  return examinersBySection.get(sectionId) ?? []
}
</script>

<template>
  <HelpSection :title="t('helpCenter.protocolRunDetail.exampleTitle')">
    <div class="flex items-center gap-2 mb-4">
      <SecondaryButton disabled>
        <font-awesome-icon :icon="['fas', 'chevron-left']" />
      </SecondaryButton>
      <SectionHeader>{{ t('helpCenter.sample.protocol.runSpring') }}</SectionHeader>
      <PrimaryBadge>{{ t('protocol.open') }}</PrimaryBadge>
      <ButtonRow align="end" class="sm:ml-auto">
        <PrimaryButton disabled>{{ t('protocol.closeRun') }}</PrimaryButton>
        <SecondaryButton :icon="['fas', 'user-check']" disabled>{{ t('protocol.plan.open') }}</SecondaryButton>
        <SecondaryButton disabled>
          <font-awesome-icon :icon="['fas', 'chart-bar']" class="mr-1" /> {{ t('protocol.evaluation') }}
        </SecondaryButton>
      </ButtonRow>
    </div>

    <div class="flex items-center gap-2 mb-4">
      <p class="text-sm text-[var(--text-muted)]">{{ t('helpCenter.sample.protocol.runSpringDay') }}</p>
      <FieldLabel inline class="ml-auto text-[var(--text-muted)]">
        <ToggleInput :model-value="false" disabled />
        {{ t('protocol.filterIncomplete') }}
      </FieldLabel>
    </div>

    <div class="space-y-2">
      <NeutralContainer v-for="rm in members" :key="rm.progress.member.id" class="flex items-center gap-2">
        <div class="flex-1 min-w-0">
          <div class="font-medium">{{ rm.name }}</div>
          <div class="text-xs text-[var(--text-muted)] flex items-center gap-2">
            <span>{{ rm.progress.sectionsDone }}/{{ rm.progress.sectionsTotal }} {{ t('protocol.sections') }}</span>
            <template v-if="rm.progress.member.lockedBy">
              <font-awesome-icon :icon="['fas', 'lock']" />
              {{ tom?.name }}
            </template>
          </div>
          <RunMemberSections class="mt-1" :sections="topSections" :done-section-ids="rm.progress.doneSectionIds"
                             :examiner-names="examinerNames" />
        </div>
        <span class="font-mono text-sm">{{ rm.progress.member.totalScore }}P</span>
        <SuccessBadge v-if="rm.progress.member.completed">{{ t('protocol.completed') }}</SuccessBadge>
        <ErrorBadge v-else-if="rm.progress.member.lockedBy">{{ t('protocol.locked') }}</ErrorBadge>
        <SecondaryBadge v-else>{{ t('protocol.pending') }}</SecondaryBadge>
        <PrimaryButton v-if="!rm.progress.member.completed" class="!text-sm !py-1 !px-3" disabled>
          <font-awesome-icon :icon="['fas', 'clipboard-check']" class="mr-1" />
          {{ t('protocol.grade') }}
        </PrimaryButton>
      </NeutralContainer>
    </div>
  </HelpSection>
</template>
