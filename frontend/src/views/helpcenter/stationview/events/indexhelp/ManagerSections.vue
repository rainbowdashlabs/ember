/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import DummyEventList from '@/views/helpcenter/stationview/events/indexhelp/DummyEventList.vue'
import DummyBreaksList from '@/views/helpcenter/stationview/events/indexhelp/DummyBreaksList.vue'
import DummyExportBar from '@/views/helpcenter/stationview/events/indexhelp/DummyExportBar.vue'

const {t} = useI18n()
</script>

<template>
  <HelpSection :title="t('helpCenter.eventsManage.createTitle')">
    <p>{{ t('helpCenter.eventsManage.createName') }}</p>
    <p>{{ t('helpCenter.eventsManage.createType') }}</p>
    <p>{{ t('helpCenter.eventsManage.createTemplate') }}</p>
    <p>{{ t('helpCenter.eventsManage.createRegistration') }}</p>
    <p>{{ t('helpCenter.eventsManage.createRestrictions') }}</p>
  </HelpSection>

  <HelpSection :title="t('helpCenter.eventsManage.categoriesTitle')">
    <p>{{ t('helpCenter.eventsManage.categoriesText') }}</p>
    <DummyEventList/>
  </HelpSection>

  <HelpSection :title="t('helpCenter.eventsManage.batchTitle')">
    <p>{{ t('helpCenter.eventsManage.batchText') }}</p>
  </HelpSection>

  <HelpSection :title="t('helpCenter.eventsManage.breaksTitle')">
    <p>{{ t('helpCenter.eventsManage.breaksText') }}</p>
    <p>{{ t('helpCenter.eventsManage.breaksImport') }}</p>
  </HelpSection>

  <HelpSection :title="t('events.breaks')">
    <DummyBreaksList/>
  </HelpSection>

  <HelpSection :title="t('helpCenter.eventsManage.exportTitle')">
    <p>{{ t('helpCenter.eventsManage.exportText') }}</p>
    <DummyExportBar/>
  </HelpSection>
</template>
