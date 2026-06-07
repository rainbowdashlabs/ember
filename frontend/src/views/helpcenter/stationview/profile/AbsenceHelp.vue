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
import DeleteButton from '@/components/button/DeleteButton.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'

const {t} = useI18n()

const perspectives: HelpPerspective[] = [
  {key: 'member', label: t('helpCenter.roles.member'), permissions: [StationPermission.USER]},
  {key: 'memberManager', label: t('helpCenter.roles.memberManager'), permissions: [StationPermission.MEMBER_GUARDIAN]},
]
const activeView = ref('')
</script>

<template>
  <HelpArticle :title="t('helpCenter.absences.title')" :subtitle="t('helpCenter.absences.subtitle')">
    <HelpSection :title="t('helpCenter.absences.whatIs')">
      <p>{{ t('helpCenter.absences.whatIsText') }}</p>
    </HelpSection>

    <HelpRoleToggle v-model="activeView" :perspectives="perspectives"/>

    <HelpSection :title="t('helpCenter.absences.howTo')">
      <p>{{ t('helpCenter.absences.fromTo') }}</p>
      <p>{{ t('helpCenter.absences.reason') }}</p>
      <template v-if="activeView === 'memberManager'">
        <p>{{ t('helpCenter.absences.forOthers') }}</p>
      </template>
    </HelpSection>

    <!-- Dummy: Add absence form -->
    <NeutralContainer class="space-y-4">
      <div class="grid gap-4 sm:grid-cols-3">
        <div class="space-y-1">
          <FieldLabel>{{ t('profile.absenceFrom') }}</FieldLabel>
          <DateInput model-value="2026-06-01"/>
        </div>
        <div class="space-y-1">
          <FieldLabel>{{ t('profile.absenceUntil') }}</FieldLabel>
          <DateInput model-value="2026-06-14"/>
        </div>
        <div class="space-y-1">
          <FieldLabel>{{ t('profile.absenceReason') }}</FieldLabel>
          <TextAreaInput model-value="Sommerurlaub" :placeholder="t('profile.absenceReasonPlaceholder')"/>
        </div>
      </div>

      <!-- Dummy: Member selection for managers -->
      <template v-if="activeView === 'memberManager'">
        <div class="space-y-2">
          <FieldLabel>{{ t('profile.absenceFor') }}</FieldLabel>
          <div class="flex flex-wrap gap-2">
            <SelectionToggleButton :selected="true" size="md">
              {{ t('profile.absenceMyself') }}
            </SelectionToggleButton>
            <SelectionToggleButton :selected="true" size="md">
              Lena Mustermann
            </SelectionToggleButton>
            <SelectionToggleButton :selected="false" size="md">
              Tim Mustermann
            </SelectionToggleButton>
          </div>
        </div>
      </template>

      <div class="flex gap-3">
        <PrimaryButton>{{ t('profile.absenceAdd') }}</PrimaryButton>
        <SecondaryButton>{{ t('common.cancel') }}</SecondaryButton>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.absences.statusTitle')">
      <p>{{ t('helpCenter.absences.statusActive') }}</p>
      <p>{{ t('helpCenter.absences.statusUpcoming') }}</p>
      <p>{{ t('helpCenter.absences.statusExpired') }}</p>
    </HelpSection>

    <!-- Dummy: Absence list -->
    <div class="space-y-2">
      <NeutralContainer>
        <div class="flex items-center justify-between flex-wrap gap-2">
          <div>
            <span class="text-sm">01.06.2026 – 14.06.2026</span>
            <MutedText size="sm" class="ml-3">Sommerurlaub</MutedText>
          </div>
          <div class="flex items-center gap-2">
            <InfoBadge>{{ t('profile.absenceUpcoming') }}</InfoBadge>
            <DeleteButton/>
          </div>
        </div>
      </NeutralContainer>
      <NeutralContainer>
        <div class="flex items-center justify-between flex-wrap gap-2">
          <div>
            <span class="text-sm">10.05.2026 – 16.05.2026</span>
            <MutedText size="sm" class="ml-3">Klassenfahrt</MutedText>
          </div>
          <div class="flex items-center gap-2">
            <SuccessBadge>{{ t('profile.absenceActive') }}</SuccessBadge>
            <DeleteButton/>
          </div>
        </div>
      </NeutralContainer>
      <NeutralContainer>
        <div class="flex items-center justify-between flex-wrap gap-2">
          <div>
            <span class="text-sm">01.04.2026 – 05.04.2026</span>
            <MutedText size="sm" class="ml-3">Krank</MutedText>
          </div>
          <div class="flex items-center gap-2">
            <ErrorBadge>{{ t('profile.absenceExpired') }}</ErrorBadge>
            <DeleteButton/>
          </div>
        </div>
      </NeutralContainer>
    </div>

    <HelpTip>{{ t('helpCenter.absences.tip') }}</HelpTip>
  </HelpArticle>
</template>
