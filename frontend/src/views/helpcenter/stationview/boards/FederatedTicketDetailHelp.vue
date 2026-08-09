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
import DummySidebar from './ticketdetailhelp/DummySidebar.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.federatedTicketDetail.title')"
               :subtitle="t('helpCenter.federatedTicketDetail.subtitle')">
    <HelpSection :title="t('helpCenter.federatedTicketDetail.whatIs')">
      <p>{{ t('helpCenter.federatedTicketDetail.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.federatedTicketDetail.howTo')">
      <p>{{ t('helpCenter.federatedTicketDetail.howToStep1') }}</p>
      <p>{{ t('helpCenter.federatedTicketDetail.howToStep2') }}</p>
      <p>{{ t('helpCenter.federatedTicketDetail.howToStep3') }}</p>
      <DummySidebar/>
    </HelpSection>

    <HelpSection :title="t('helpCenter.federatedTicketDetail.readOnlyTitle')">
      <p>{{ t('helpCenter.federatedTicketDetail.readOnlyText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.federatedTicketDetail.detailTitle')">
      <p>{{ t('helpCenter.federatedTicketDetail.detailText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.federatedTicketDetail.tip') }}</HelpTip>
  </HelpArticle>
</template>
