/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import HelpPermissionGuard from '@/components/helpcenter/HelpPermissionGuard.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import TabBar from '@/components/navigation/TabBar.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import BulletList from '@/components/typography/BulletList.vue'
import {StationPermission} from '@/api/types'

const { t } = useI18n()

const tabs = [
  { key: 'tests', label: 'Tests' },
  { key: 'results', label: 'Ergebnisse' },
]
</script>

<template>
  <HelpArticle :title="t('helpCenter.quiz.testListTitle')" :subtitle="t('helpCenter.quiz.testListSubtitle')">
    <HelpSection :title="t('helpCenter.quiz.whatIsTest')">
      <p>{{ t('helpCenter.quiz.whatIsTestText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.quiz.testListHow')">
      <p>{{ t('helpCenter.quiz.testListHowText') }}</p>
    </HelpSection>

    <!-- Dummy: Test list with TabBar -->
    <HelpSection :title="t('helpCenter.quiz.testListExampleTitle')">
      <div class="space-y-3">
        <div class="flex items-center justify-between">
          <SectionHeader>{{ t('quiz.tests.title') }}</SectionHeader>
          <HelpPermissionGuard :permissions="[StationPermission.TEST_CONFIGURE]" :label="t('helpCenter.permissionLabel.testConfigure')">
            <PrimaryButton :icon="['fas', 'plus']" disabled>
              {{ t('quiz.tests.create') }}
            </PrimaryButton>
          </HelpPermissionGuard>
        </div>

        <TabBar :model-value="'tests'" :tabs="tabs" />

        <!-- Test cards (clickable, no Open button) -->
        <NeutralContainer class="cursor-pointer">
          <div class="flex items-center justify-between gap-4">
            <div class="flex-1 space-y-1">
              <div class="flex items-center gap-2 flex-wrap">
                <span class="font-medium">Brandschutz-Prüfung</span>
                <SuccessBadge>{{ t('quiz.tests.statusActive') }}</SuccessBadge>
              </div>
              <p class="text-xs text-(--text-muted) line-clamp-1">Prüfung zum Thema Brandschutz</p>
            </div>
            <div class="flex items-center gap-4 text-xs text-(--text-muted) shrink-0">
              <span>12 {{ t('quiz.attemptCount') }}</span>
              <div class="flex items-center gap-2" @click.stop>
                <PrimaryButton disabled>{{ t('quiz.tests.takeTest') }}</PrimaryButton>
                <HelpPermissionGuard :permissions="[StationPermission.TEST_CONFIGURE]" :label="t('helpCenter.permissionLabel.testConfigure')">
                  <SecondaryButton disabled>{{ t('common.edit') }}</SecondaryButton>
                  <ErrorButton disabled>{{ t('common.delete') }}</ErrorButton>
                </HelpPermissionGuard>
              </div>
            </div>
          </div>
        </NeutralContainer>

        <NeutralContainer class="cursor-pointer">
          <div class="flex items-center justify-between gap-4">
            <div class="flex-1 space-y-1">
              <div class="flex items-center gap-2 flex-wrap">
                <span class="font-medium">Erste-Hilfe-Test</span>
                <SecondaryBadge>{{ t('quiz.tests.statusDraft') }}</SecondaryBadge>
              </div>
              <p class="text-xs text-(--text-muted) line-clamp-1">Entwurf für den nächsten Übungsabend</p>
            </div>
            <div class="flex items-center gap-4 text-xs text-(--text-muted) shrink-0">
              <span>0 {{ t('quiz.attemptCount') }}</span>
              <HelpPermissionGuard :permissions="[StationPermission.TEST_CONFIGURE]" :label="t('helpCenter.permissionLabel.testConfigure')">
                <div class="flex items-center gap-2" @click.stop>
                  <SecondaryButton disabled>{{ t('common.edit') }}</SecondaryButton>
                  <ErrorButton disabled>{{ t('common.delete') }}</ErrorButton>
                </div>
              </HelpPermissionGuard>
            </div>
          </div>
        </NeutralContainer>

        <NeutralContainer class="cursor-pointer">
          <div class="flex items-center justify-between gap-4">
            <div class="flex-1 space-y-1">
              <div class="flex items-center gap-2 flex-wrap">
                <span class="font-medium">Knoten-Quiz</span>
                <ErrorBadge>{{ t('quiz.tests.statusClosed') }}</ErrorBadge>
                <InfoBadge>{{ t('quiz.tests.taken') }}</InfoBadge>
              </div>
              <p class="text-xs text-(--text-muted) line-clamp-1">Abgeschlossener Test</p>
            </div>
            <div class="flex items-center gap-4 text-xs text-(--text-muted) shrink-0">
              <span>5 {{ t('quiz.attemptCount') }}</span>
            </div>
          </div>
        </NeutralContainer>
      </div>
    </HelpSection>

    <HelpPermissionGuard :permissions="[StationPermission.TEST_CONFIGURE]" :label="t('helpCenter.permissionLabel.testConfigure')">
      <HelpSection :title="t('helpCenter.quiz.createTestTitle')">
        <p>{{ t('helpCenter.quiz.createTestText') }}</p>
        <BulletList class="mt-2">
          <li>{{ t('helpCenter.quiz.createTestName') }}</li>
          <li>{{ t('helpCenter.quiz.createTestDesc') }}</li>
          <li>{{ t('helpCenter.quiz.createTestTime') }}</li>
          <li>{{ t('helpCenter.quiz.createTestShuffle') }}</li>
        </BulletList>
      </HelpSection>
    </HelpPermissionGuard>

    <HelpTip>{{ t('helpCenter.quiz.testListTip') }}</HelpTip>
  </HelpArticle>
</template>
