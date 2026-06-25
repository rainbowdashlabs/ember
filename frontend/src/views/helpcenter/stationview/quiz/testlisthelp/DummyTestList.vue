/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {StationPermission} from '@/api/types'
import HelpPermissionGuard from '@/components/helpcenter/HelpPermissionGuard.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import TabBar from '@/components/navigation/TabBar.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import DummyTestCard from '@/views/helpcenter/stationview/quiz/testlisthelp/DummyTestCard.vue'

const {t} = useI18n()

const tabs = [
  {key: 'tests', label: 'Tests'},
  {key: 'results', label: 'Ergebnisse'},
]
</script>

<template>
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

    <DummyTestCard name="Brandschutz-Prüfung" description="Prüfung zum Thema Brandschutz" :attempts="`12 ${t('quiz.attemptCount')}`">
      <template #badges>
        <SuccessBadge>{{ t('quiz.tests.statusActive') }}</SuccessBadge>
      </template>
      <template #actions>
        <div class="flex items-center gap-2" @click.stop>
          <PrimaryButton disabled>{{ t('quiz.tests.takeTest') }}</PrimaryButton>
          <HelpPermissionGuard :permissions="[StationPermission.TEST_CONFIGURE]" :label="t('helpCenter.permissionLabel.testConfigure')">
            <SecondaryButton disabled>{{ t('common.edit') }}</SecondaryButton>
            <ErrorButton disabled>{{ t('common.delete') }}</ErrorButton>
          </HelpPermissionGuard>
        </div>
      </template>
    </DummyTestCard>

    <DummyTestCard name="Erste-Hilfe-Test" description="Entwurf für den nächsten Übungsabend" :attempts="`0 ${t('quiz.attemptCount')}`">
      <template #badges>
        <SecondaryBadge>{{ t('quiz.tests.statusDraft') }}</SecondaryBadge>
      </template>
      <template #actions>
        <HelpPermissionGuard :permissions="[StationPermission.TEST_CONFIGURE]" :label="t('helpCenter.permissionLabel.testConfigure')">
          <div class="flex items-center gap-2" @click.stop>
            <SecondaryButton disabled>{{ t('common.edit') }}</SecondaryButton>
            <ErrorButton disabled>{{ t('common.delete') }}</ErrorButton>
          </div>
        </HelpPermissionGuard>
      </template>
    </DummyTestCard>

    <DummyTestCard name="Knoten-Quiz" description="Abgeschlossener Test" :attempts="`5 ${t('quiz.attemptCount')}`">
      <template #badges>
        <ErrorBadge>{{ t('quiz.tests.statusClosed') }}</ErrorBadge>
        <InfoBadge>{{ t('quiz.tests.taken') }}</InfoBadge>
      </template>
    </DummyTestCard>
  </div>
</template>
