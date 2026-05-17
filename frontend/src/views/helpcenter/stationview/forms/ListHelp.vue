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
import type {HelpRole} from '@/components/helpcenter/HelpRoleToggle.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'

const {t} = useI18n()

const roles: HelpRole[] = [
  {key: 'member', label: t('helpCenter.roles.member')},
  {key: 'manager', label: t('helpCenter.roles.manager')},
]
const activeRole = ref('')
</script>

<template>
  <HelpArticle :title="t('helpCenter.forms.title')" :subtitle="t('helpCenter.forms.subtitle')">
    <HelpSection :title="t('helpCenter.forms.whatIs')">
      <p>{{ t('helpCenter.forms.whatIsText') }}</p>
    </HelpSection>

    <HelpRoleToggle v-model="activeRole" :roles="roles"/>

    <!-- Dummy: Form list -->
    <div class="space-y-3">
      <div class="flex items-center justify-between">
        <h3 class="font-semibold">{{ t('forms.title') }}</h3>
        <PrimaryButton v-if="activeRole === 'manager'" class="text-sm">
          <font-awesome-icon :icon="['fas', 'plus']" class="mr-1"/>
          {{ t('forms.create') }}
        </PrimaryButton>
      </div>

      <NeutralContainer>
        <div class="flex items-center justify-between">
          <div class="space-y-1">
            <div class="flex items-center gap-2">
              <span class="font-medium">Zufriedenheitsumfrage</span>
              <SuccessBadge>{{ t('forms.statusOpen') }}</SuccessBadge>
            </div>
            <p class="text-xs text-(--text-muted)">Wie gefällt dir unsere Jugendfeuerwehr?</p>
          </div>
          <div class="flex gap-2">
            <PrimaryButton v-if="activeRole === 'member'" class="text-xs">{{ t('forms.fillForm') }}</PrimaryButton>
            <template v-if="activeRole === 'manager'">
              <SecondaryButton class="text-xs">{{ t('forms.viewAnalytics') }}</SecondaryButton>
              <SecondaryButton class="text-xs">{{ t('forms.close') }}</SecondaryButton>
            </template>
          </div>
        </div>
      </NeutralContainer>

      <NeutralContainer>
        <div class="flex items-center justify-between">
          <div class="space-y-1">
            <div class="flex items-center gap-2">
              <span class="font-medium">Feedback Übungsabend</span>
              <ErrorBadge>{{ t('forms.statusClosed') }}</ErrorBadge>
            </div>
            <p class="text-xs text-(--text-muted)">Rückmeldung zum letzten Übungsabend</p>
          </div>
          <SecondaryButton v-if="activeRole === 'manager'" class="text-xs">{{ t('forms.viewAnalytics') }}</SecondaryButton>
        </div>
      </NeutralContainer>

      <NeutralContainer v-if="activeRole === 'manager'">
        <div class="flex items-center justify-between">
          <div class="space-y-1">
            <div class="flex items-center gap-2">
              <span class="font-medium">Neues Formular (Entwurf)</span>
              <InfoBadge>{{ t('forms.statusDraft') }}</InfoBadge>
            </div>
          </div>
          <div class="flex gap-2">
            <SecondaryButton class="text-xs">{{ t('forms.publish') }}</SecondaryButton>
            <SecondaryButton class="text-xs">{{ t('forms.edit') }}</SecondaryButton>
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
      <ul class="list-disc pl-5 space-y-1 mt-2">
        <li>{{ t('helpCenter.forms.typeChoice') }}</li>
        <li>{{ t('helpCenter.forms.typeText') }}</li>
        <li>{{ t('helpCenter.forms.typeRating') }}</li>
        <li>{{ t('helpCenter.forms.typeDate') }}</li>
        <li>{{ t('helpCenter.forms.typeRanking') }}</li>
        <li>{{ t('helpCenter.forms.typeLikert') }}</li>
      </ul>
    </HelpSection>

    <template v-if="activeRole === 'manager'">
      <HelpSection :title="t('helpCenter.forms.managerTitle')">
        <p>{{ t('helpCenter.forms.managerText') }}</p>
      </HelpSection>

      <HelpSection :title="t('helpCenter.forms.builderTitle')">
        <p>{{ t('helpCenter.forms.builderText') }}</p>
      </HelpSection>

      <HelpSection :title="t('helpCenter.forms.lifecycleTitle')">
        <p>{{ t('helpCenter.forms.lifecycleText') }}</p>
        <ul class="list-disc pl-5 space-y-1 mt-2">
          <li><strong>{{ t('forms.statusDraft') }}:</strong> {{ t('helpCenter.forms.statusDraftDesc') }}</li>
          <li><strong>{{ t('forms.statusOpen') }}:</strong> {{ t('helpCenter.forms.statusOpenDesc') }}</li>
          <li><strong>{{ t('forms.statusClosed') }}:</strong> {{ t('helpCenter.forms.statusClosedDesc') }}</li>
        </ul>
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
