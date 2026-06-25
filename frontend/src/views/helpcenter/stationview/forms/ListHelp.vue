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
import BulletList from '@/components/typography/BulletList.vue'
import DummyFormList from '@/views/helpcenter/stationview/forms/listhelp/DummyFormList.vue'

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

    <DummyFormList :view="activeView"/>

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
