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
import HelpList from '@/components/helpcenter/HelpList.vue'
import DummyFormPreview from './ticketcreatehelp/DummyFormPreview.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.ticketCreate.title')" :subtitle="t('helpCenter.ticketCreate.subtitle')">
    <HelpSection :title="t('helpCenter.ticketCreate.whatIs')">
      <p>{{ t('helpCenter.ticketCreate.whatIsText') }}</p>
      <p>{{ t('helpCenter.ticketCreate.whatIsText2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.ticketCreate.formTitle')">
      <p>{{ t('helpCenter.ticketCreate.formText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.ticketCreate.exampleTitle')">
      <DummyFormPreview />
    </HelpSection>

    <HelpSection :title="t('helpCenter.ticketCreate.startLaneTitle')">
      <p>{{ t('helpCenter.ticketCreate.startLaneText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.ticketCreate.linkTypesTitle')">
      <p>{{ t('helpCenter.ticketCreate.linkTypesText') }}</p>
      <HelpList>
        <li><strong>{{ t('boards.linkRelatesTo') }}:</strong> {{ t('helpCenter.ticketCreate.linkRelatesDesc') }}</li>
        <li><strong>{{ t('boards.linkBlocks') }} / {{ t('boards.linkBlockedBy') }}:</strong> {{ t('helpCenter.ticketCreate.linkBlocksDesc') }}</li>
        <li><strong>{{ t('boards.linkCauses') }} / {{ t('boards.linkCausedBy') }}:</strong> {{ t('helpCenter.ticketCreate.linkCausesDesc') }}</li>
      </HelpList>
    </HelpSection>

    <HelpSection :title="t('helpCenter.ticketCreate.mentionsTitle')">
      <p>{{ t('helpCenter.ticketCreate.mentionsText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.ticketCreate.tip') }}</HelpTip>
  </HelpArticle>
</template>
