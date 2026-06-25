/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import DummyRequestItem from './DummyRequestItem.vue'

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SubHeader>{{ t('helpCenter.inventoryLending.dummyIncoming') }}</SubHeader>
    <DummyRequestItem station="DLRG Neustadt" range="01.06.2026 - 15.06.2026" item="Rettungsring (3x)">
      <SecondaryBadge>{{ t('helpCenter.inventoryLending.statusRequested') }}</SecondaryBadge>
    </DummyRequestItem>
    <DummyRequestItem station="DLRG Beispielburg" range="10.05.2026 - 20.05.2026" item="Taucheranzug (1x)">
      <SuccessBadge>{{ t('helpCenter.inventoryLending.statusLent') }}</SuccessBadge>
    </DummyRequestItem>

    <SubHeader class="mt-4">{{ t('helpCenter.inventoryLending.dummyOutgoing') }}</SubHeader>
    <DummyRequestItem station="DLRG Musterstadt" range="05.07.2026 - 10.07.2026" item="Erste-Hilfe-Koffer (2x)">
      <SuccessBadge>{{ t('helpCenter.inventoryLending.statusApproved') }}</SuccessBadge>
    </DummyRequestItem>
    <DummyRequestItem station="DLRG Neustadt" range="01.04.2026 - 05.04.2026" item="Schwimmweste (1x)">
      <ErrorBadge>{{ t('helpCenter.inventoryLending.statusDeclined') }}</ErrorBadge>
    </DummyRequestItem>
    <DummyRequestItem station="DLRG Musterstadt" range="20.03.2026 - 25.03.2026" item="Schwimmflossen (4x)">
      <SecondaryBadge>{{ t('helpCenter.inventoryLending.statusReturned') }}</SecondaryBadge>
    </DummyRequestItem>
    <DummyRequestItem station="DLRG Beispielburg" range="01.02.2026 - 10.02.2026" item="Taucherbrille (2x)">
      <InfoBadge>{{ t('helpCenter.inventoryLending.statusClosed') }}</InfoBadge>
    </DummyRequestItem>
  </NeutralContainer>
</template>
