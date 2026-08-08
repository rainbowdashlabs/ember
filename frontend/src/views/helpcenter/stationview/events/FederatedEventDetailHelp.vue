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
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import HeaderCard from '@/views/stationview/events/federatedeventdetailview/HeaderCard.vue'

const {t} = useI18n()

const EVENT = {
  name: 'Gemeinsame Übung mit der Nachbarwache',
  eventType: 'ONE_TIME',
  requiresRegistration: true,
  dayOfWeek: 6,
  startTime: '09:00:00',
  endTime: '12:00:00',
  description: 'Treffpunkt ist der Hof der Wache. Bringt eure Einsatzkleidung mit.',
}

const PUBLIC_FIELDS = [
  {id: 1, name: 'Treffpunkt', value: 'Hof der Wache', fieldType: 'STRING', isPublic: true},
]
</script>

<template>
  <HelpArticle :title="t('helpCenter.federatedEventDetail.title')"
               :subtitle="t('helpCenter.federatedEventDetail.subtitle')">
    <HelpSection :title="t('helpCenter.federatedEventDetail.whatIs')">
      <p>{{ t('helpCenter.federatedEventDetail.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.federatedEventDetail.howTo')">
      <p>{{ t('helpCenter.federatedEventDetail.howToStep1') }}</p>
      <p>{{ t('helpCenter.federatedEventDetail.howToStep2') }}</p>
      <p>{{ t('helpCenter.federatedEventDetail.howToStep3') }}</p>
      <div class="space-y-4">
        <SecondaryButton :icon="['fas', 'arrow-left']" compact>{{ t('common.back') }}</SecondaryButton>
        <HeaderCard :event="EVENT" :public-fields="PUBLIC_FIELDS"/>
      </div>
    </HelpSection>

    <HelpSection :title="t('helpCenter.federatedEventDetail.registrationTitle')">
      <p>{{ t('helpCenter.federatedEventDetail.registrationText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.federatedEventDetail.visibleTitle')">
      <p>{{ t('helpCenter.federatedEventDetail.visibleText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.federatedEventDetail.tip') }}</HelpTip>
  </HelpArticle>
</template>
