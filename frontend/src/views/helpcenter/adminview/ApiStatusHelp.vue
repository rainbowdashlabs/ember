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
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import Td from '@/components/table/Td.vue'
import Th from '@/components/table/Th.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.adminApiStatus.title')" :subtitle="t('helpCenter.adminApiStatus.subtitle')">
    <HelpSection :title="t('helpCenter.adminApiStatus.whatIs')">
      <p>{{ t('helpCenter.adminApiStatus.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminApiStatus.metricsTitle')">
      <p>{{ t('helpCenter.adminApiStatus.metricsText') }}</p>
    </HelpSection>

    <!-- Dummy: endpoint table -->
    <NeutralContainer>
      <table class="w-full text-sm">
        <thead>
          <tr class="border-b border-[var(--border)]">
            <th class="text-left py-2 px-3 font-medium">Endpunkt</th>
            <th class="text-right py-2 px-3 font-medium">Ø Zeit</th>
            <th class="text-right py-2 px-3 font-medium">Anfragen</th>
            <Th align="center" class="font-medium">Status</th>
          </tr>
        </thead>
        <tbody>
          <tr class="border-b border-[var(--border)]">
            <Td class="font-mono text-xs">GET /api/v1/members</Td>
            <Td align="right">12 ms</Td>
            <Td align="right">1 240</Td>
            <Td align="center"><SuccessBadge>OK</SuccessBadge></Td>
          </tr>
          <tr class="border-b border-[var(--border)]">
            <Td class="font-mono text-xs">POST /api/v1/quiz/submit</Td>
            <Td align="right">45 ms</Td>
            <Td align="right">382</Td>
            <Td align="center"><SuccessBadge>OK</SuccessBadge></Td>
          </tr>
          <tr class="border-b border-[var(--border)]">
            <Td class="font-mono text-xs">GET /api/v1/kb/search</Td>
            <Td align="right">210 ms</Td>
            <Td align="right">94</Td>
            <Td align="center"><InfoBadge>Langsam</InfoBadge></Td>
          </tr>
          <tr>
            <Td class="font-mono text-xs">POST /api/v1/mail/send</Td>
            <Td align="right">—</Td>
            <Td align="right">3</Td>
            <Td align="center"><ErrorBadge>Fehler</ErrorBadge></Td>
          </tr>
        </tbody>
      </table>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.adminApiStatus.responseTitle')">
      <p>{{ t('helpCenter.adminApiStatus.responseText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminApiStatus.errorsTitle')">
      <p>{{ t('helpCenter.adminApiStatus.errorsText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.adminApiStatus.tip') }}</HelpTip>
  </HelpArticle>
</template>
