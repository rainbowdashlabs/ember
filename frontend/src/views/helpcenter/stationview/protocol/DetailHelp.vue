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
import ProtocolStructureExampleSection from './detailhelp/ProtocolStructureExampleSection.vue'
import ProtocolEditorDemo from './detailhelp/ProtocolEditorDemo.vue'
import BonusPointExampleSection from './detailhelp/BonusPointExampleSection.vue'
import { DEMO_SECTION } from './fixtures'

const { t } = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.protocolDetail.title')" :subtitle="t('helpCenter.protocolDetail.subtitle')">
    <HelpSection :title="t('helpCenter.protocolDetail.whatIs')">
      <p>{{ t('helpCenter.protocolDetail.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.protocolDetail.structureTitle')">
      <p>{{ t('helpCenter.protocolDetail.structureText') }}</p>
      <p>{{ t('helpCenter.protocolDetail.depthText') }}</p>
    </HelpSection>

    <ProtocolStructureExampleSection />

    <HelpPermissionGuard :permissions="[StationPermission.PROTOCOL_CONFIGURE]"
                         :label="t('helpCenter.permissionLabel.protocolConfigure')">
      <HelpSection :title="t('helpCenter.protocolDetail.actionsTitle')">
        <p>{{ t('helpCenter.protocolDetail.actionsText') }}</p>
        <p>{{ t('helpCenter.protocolDetail.orderText') }}</p>
      </HelpSection>

      <HelpSection :title="t('helpCenter.protocolDetail.cutTitle')">
        <p>{{ t('helpCenter.protocolDetail.cutText') }}</p>
        <p>{{ t('helpCenter.protocolDetail.pasteText') }}</p>
        <ProtocolEditorDemo :cut-section-id="DEMO_SECTION.advancedKnots" />
      </HelpSection>

      <BonusPointExampleSection />
    </HelpPermissionGuard>

    <HelpSection :title="t('helpCenter.protocolDetail.thresholdTitle')">
      <p>{{ t('helpCenter.protocolDetail.thresholdText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.protocolDetail.howTo')">
      <p>{{ t('helpCenter.protocolDetail.step1') }}</p>
      <p>{{ t('helpCenter.protocolDetail.step2') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.protocolDetail.tip') }}</HelpTip>
  </HelpArticle>
</template>
