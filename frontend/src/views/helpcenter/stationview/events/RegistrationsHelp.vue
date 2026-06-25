/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import RegistrationsNormalGroup from './registrationshelp/RegistrationsNormalGroup.vue'
import RegistrationsExpiredGroup from './registrationshelp/RegistrationsExpiredGroup.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.eventsRegistrations.title')" :subtitle="t('helpCenter.eventsRegistrations.subtitle')">
    <HelpSection :title="t('helpCenter.eventsRegistrations.whatShown')">
      <p>{{ t('helpCenter.eventsRegistrations.whatShownText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventsRegistrations.badgeTitle')">
      <p>{{ t('helpCenter.eventsRegistrations.badgeText') }}</p>
    </HelpSection>

    <HelpSection :title="t('eventsRegistrations.title')">
      <SectionHeader>{{ t('eventsRegistrations.title') }}</SectionHeader>
      <div class="space-y-3">
        <RegistrationsNormalGroup/>
        <RegistrationsExpiredGroup/>
      </div>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventsRegistrations.fairnessTitle')">
      <p>{{ t('helpCenter.eventsRegistrations.fairnessText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventsRegistrations.deadlineTitle')">
      <p>{{ t('helpCenter.eventsRegistrations.deadlineText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.eventsRegistrations.tip') }}</HelpTip>
  </HelpArticle>
</template>
