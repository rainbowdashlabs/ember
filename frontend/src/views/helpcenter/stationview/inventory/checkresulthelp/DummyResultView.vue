/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import DummyResultCard from '@/views/helpcenter/stationview/inventory/checkresulthelp/DummyResultCard.vue'

const {t} = useI18n()
</script>

<template>
  <div class="space-y-3">
    <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
      <div>
        <SectionHeader>Max Mustermann</SectionHeader>
        <p class="text-sm text-(--text-muted)">{{ t('inventory.check.lastResult') }}</p>
      </div>
      <SecondaryButton :icon="['fas', 'chevron-left']">
        {{ t('inventory.check.backToOverview') }}
      </SecondaryButton>
    </div>

    <NeutralContainer>
      <div class="text-sm text-(--text-muted)">
        10.05.2026, 14:30 &middot; {{ t('inventory.check.checkedBy') }}: Admin User
      </div>
    </NeutralContainer>

    <DummyResultCard variant="success" name="Helm" size="M" category="Helme" inventory-id="INV-0001">
      <SuccessBadge class="self-start">{{ t('inventory.check.resultConfirmed') }}</SuccessBadge>
    </DummyResultCard>

    <DummyResultCard variant="error" name="Jacke" size="L" category="Jacken" inventory-id="INV-0015" note="Konnte nicht gefunden werden">
      <ErrorBadge class="self-start">{{ t('inventory.check.resultLost') }}</ErrorBadge>
    </DummyResultCard>

    <DummyResultCard variant="info" name="Stiefel" size="42" category="Stiefel" inventory-id="INV-0030">
      <InfoBadge class="self-start">{{ t('inventory.check.resultNotInPossession') }}</InfoBadge>
    </DummyResultCard>
  </div>
</template>
