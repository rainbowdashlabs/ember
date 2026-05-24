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
            <th class="text-center py-2 px-3 font-medium">Status</th>
          </tr>
        </thead>
        <tbody>
          <tr class="border-b border-[var(--border)]">
            <td class="py-2 px-3 font-mono text-xs">GET /api/v1/members</td>
            <td class="py-2 px-3 text-right">12 ms</td>
            <td class="py-2 px-3 text-right">1 240</td>
            <td class="py-2 px-3 text-center"><SuccessBadge>OK</SuccessBadge></td>
          </tr>
          <tr class="border-b border-[var(--border)]">
            <td class="py-2 px-3 font-mono text-xs">POST /api/v1/quiz/submit</td>
            <td class="py-2 px-3 text-right">45 ms</td>
            <td class="py-2 px-3 text-right">382</td>
            <td class="py-2 px-3 text-center"><SuccessBadge>OK</SuccessBadge></td>
          </tr>
          <tr class="border-b border-[var(--border)]">
            <td class="py-2 px-3 font-mono text-xs">GET /api/v1/kb/search</td>
            <td class="py-2 px-3 text-right">210 ms</td>
            <td class="py-2 px-3 text-right">94</td>
            <td class="py-2 px-3 text-center"><InfoBadge>Langsam</InfoBadge></td>
          </tr>
          <tr>
            <td class="py-2 px-3 font-mono text-xs">POST /api/v1/mail/send</td>
            <td class="py-2 px-3 text-right">—</td>
            <td class="py-2 px-3 text-right">3</td>
            <td class="py-2 px-3 text-center"><ErrorBadge>Fehler</ErrorBadge></td>
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
