/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import HelpRoleToggle from '@/components/helpcenter/HelpRoleToggle.vue'
import type {HelpPerspective} from '@/components/helpcenter/HelpRoleToggle.vue'
import {StationPermission} from '@/api/types'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import MutedIcon from '@/components/display/MutedIcon.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import BulletList from '@/components/typography/BulletList.vue'

const {t} = useI18n()

const perspectives: HelpPerspective[] = [
  {key: 'member', label: t('helpCenter.roles.member'), permissions: [StationPermission.USER]},
  {key: 'manager', label: t('helpCenter.roles.manager'), permissions: [StationPermission.POLL_MANAGER]},
]
const activeView = ref('')
</script>

<template>
  <HelpArticle :title="t('helpCenter.forms.title')" :subtitle="t('helpCenter.forms.subtitle')">
    <HelpSection :title="t('helpCenter.forms.whatIs')">
      <p>{{ t('helpCenter.forms.whatIsText') }}</p>
    </HelpSection>

    <HelpRoleToggle v-model="activeView" :perspectives="perspectives"/>

    <!-- Dummy: Form list -->
    <div class="space-y-3">
      <div class="flex items-center justify-between">
        <SubHeader>{{ t('forms.title') }}</SubHeader>
        <PrimaryButton :icon="['fas', 'plus']" v-if="activeView === 'manager'">
          {{ t('forms.create') }}
        </PrimaryButton>
      </div>

      <NeutralContainer>
        <div class="flex items-center justify-between">
          <div class="space-y-1">
            <div class="flex items-center gap-2 flex-wrap">
              <span class="font-medium">Zufriedenheitsumfrage</span>
              <MutedIcon v-if="activeView === 'manager'" :icon="['fas', 'lock']" class="ml-1"/>
              <SuccessBadge>{{ t('forms.statusOpen') }}</SuccessBadge>
            </div>
            <p class="text-xs text-(--text-muted)">Wie gefällt dir unsere Jugendfeuerwehr?</p>
          </div>
          <div class="flex gap-2">
            <PrimaryButton v-if="activeView === 'member'">{{ t('forms.fillForm') }}</PrimaryButton>
            <template v-if="activeView === 'manager'">
              <SecondaryButton>{{ t('forms.viewAnalytics') }}</SecondaryButton>
              <SecondaryButton>{{ t('forms.close') }}</SecondaryButton>
              <ErrorButton>{{ t('forms.delete') }}</ErrorButton>
            </template>
          </div>
        </div>
      </NeutralContainer>

      <!-- Member: already answered form -->
      <NeutralContainer v-if="activeView === 'member'">
        <div class="flex items-center justify-between">
          <div class="space-y-1">
            <div class="flex items-center gap-2">
              <span class="font-medium">Feedback Übungsabend</span>
              <SuccessBadge>{{ t('forms.statusOpen') }}</SuccessBadge>
            </div>
            <p class="text-xs text-(--text-muted)">Rückmeldung zum letzten Übungsabend</p>
          </div>
          <SecondaryButton>{{ t('forms.editResponse') }}</SecondaryButton>
        </div>
      </NeutralContainer>

      <NeutralContainer v-if="activeView === 'manager'">
        <div class="flex items-center justify-between">
          <div class="space-y-1">
            <div class="flex items-center gap-2">
              <span class="font-medium">Feedback Übungsabend</span>
              <ErrorBadge>{{ t('forms.statusClosed') }}</ErrorBadge>
            </div>
            <p class="text-xs text-(--text-muted)">Rückmeldung zum letzten Übungsabend</p>
          </div>
          <div class="flex gap-2">
            <SecondaryButton>{{ t('forms.viewAnalytics') }}</SecondaryButton>
            <ErrorButton>{{ t('forms.delete') }}</ErrorButton>
          </div>
        </div>
      </NeutralContainer>

      <NeutralContainer v-if="activeView === 'manager'">
        <div class="flex items-center justify-between">
          <div class="space-y-1">
            <div class="flex items-center gap-2">
              <span class="font-medium">Neues Formular (Entwurf)</span>
              <InfoBadge>{{ t('forms.statusDraft') }}</InfoBadge>
            </div>
          </div>
          <div class="flex gap-2">
            <SecondaryButton>{{ t('forms.publish') }}</SecondaryButton>
            <SecondaryButton>{{ t('forms.edit') }}</SecondaryButton>
            <ErrorButton>{{ t('forms.delete') }}</ErrorButton>
          </div>
        </div>
      </NeutralContainer>
    </div>

    <!-- Member-specific explanation -->
    <HelpSection :title="t('helpCenter.forms.fillingTitle')">
      <p>{{ t('helpCenter.forms.fillingText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.forms.questionTypesTitle')">
      <p>{{ t('helpCenter.forms.questionTypesText') }}</p>
      <BulletList class="mt-2">
        <li>{{ t('helpCenter.forms.typeChoice') }}</li>
        <li>{{ t('helpCenter.forms.typeText') }}</li>
        <li>{{ t('helpCenter.forms.typeRating') }}</li>
        <li>{{ t('helpCenter.forms.typeDate') }}</li>
        <li>{{ t('helpCenter.forms.typeRanking') }}</li>
        <li>{{ t('helpCenter.forms.typeLikert') }}</li>
      </BulletList>
    </HelpSection>

    <template v-if="activeView === 'manager'">
      <HelpSection :title="t('helpCenter.forms.managerTitle')">
        <p>{{ t('helpCenter.forms.managerText') }}</p>
      </HelpSection>

      <HelpSection :title="t('helpCenter.forms.builderTitle')">
        <p>{{ t('helpCenter.forms.builderText') }}</p>
      </HelpSection>

      <HelpSection :title="t('helpCenter.forms.lifecycleTitle')">
        <p>{{ t('helpCenter.forms.lifecycleText') }}</p>
        <BulletList class="mt-2">
          <li><strong>{{ t('forms.statusDraft') }}:</strong> {{ t('helpCenter.forms.statusDraftDesc') }}</li>
          <li><strong>{{ t('forms.statusOpen') }}:</strong> {{ t('helpCenter.forms.statusOpenDesc') }}</li>
          <li><strong>{{ t('forms.statusClosed') }}:</strong> {{ t('helpCenter.forms.statusClosedDesc') }}</li>
        </BulletList>
      </HelpSection>

      <HelpSection :title="t('helpCenter.forms.analyticsTitle')">
        <p>{{ t('helpCenter.forms.analyticsText') }}</p>
      </HelpSection>

      <HelpSection :title="t('helpCenter.forms.restrictionsTitle')">
        <p>{{ t('helpCenter.forms.restrictionsText') }}</p>
      </HelpSection>
    </template>

    <HelpTip>{{ t('helpCenter.forms.tip') }}</HelpTip>
  </HelpArticle>
</template>
