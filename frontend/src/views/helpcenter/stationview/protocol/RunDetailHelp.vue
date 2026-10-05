/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import HelpPermissionGuard from '@/components/helpcenter/HelpPermissionGuard.vue'
import { StationPermission } from '@/api/generated/schema'
import RunDetailExampleSection from './rundetailhelp/RunDetailExampleSection.vue'

const { t } = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.protocolRunDetail.title')" :subtitle="t('helpCenter.protocolRunDetail.subtitle')">
    <HelpSection :title="t('helpCenter.protocolRunDetail.whatIs')">
      <p>{{ t('helpCenter.protocolRunDetail.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.protocolRunDetail.membersTitle')">
      <p>{{ t('helpCenter.protocolRunDetail.membersText') }}</p>
      <p>{{ t('helpCenter.protocolRunDetail.statusText') }}</p>
    </HelpSection>

    <RunDetailExampleSection />

    <HelpSection :title="t('helpCenter.protocolRunDetail.sectionsTitle')">
      <p>{{ t('helpCenter.protocolRunDetail.sectionsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.protocolRunDetail.howTo')">
      <p>{{ t('helpCenter.protocolRunDetail.step1') }}</p>
      <p>{{ t('helpCenter.protocolRunDetail.step2') }}</p>
      <p>{{ t('helpCenter.protocolRunDetail.gradeButtonText') }}</p>
    </HelpSection>

    <HelpPermissionGuard :permissions="[StationPermission.PROTOCOL_CREATE]"
                         :label="t('helpCenter.permissionLabel.protocolCreate')">
      <HelpSection :title="t('helpCenter.protocolRunDetail.planTitle')">
        <p>{{ t('helpCenter.protocolRunDetail.planText') }}</p>
      </HelpSection>
    </HelpPermissionGuard>

    <HelpPermissionGuard :permissions="[StationPermission.PROTOCOL_MANAGER]"
                         :label="t('helpCenter.permissionLabel.protocolManager')">
      <HelpSection :title="t('helpCenter.protocolRunDetail.closeTitle')">
        <p>{{ t('helpCenter.protocolRunDetail.closeText') }}</p>
      </HelpSection>
    </HelpPermissionGuard>

    <HelpTip>{{ t('helpCenter.protocolRunDetail.tip') }}</HelpTip>
  </HelpArticle>
</template>
