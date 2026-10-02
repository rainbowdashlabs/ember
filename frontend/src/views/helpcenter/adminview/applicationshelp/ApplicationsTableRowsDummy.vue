/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import Td from '@/components/table/Td.vue'
import TRow from '@/components/table/TRow.vue'

const {t} = useI18n()
</script>

<template>
  <tbody>
  <TRow>
    <Td>
      <div class="font-medium">{{ t('helpCenter.sample.people.lisaBeispiel') }}</div>
      <div class="text-xs text-(--text-muted) mt-0.5">{{ t('helpCenter.sample.admin.applicationReason') }}</div>
    </Td>
    <Td muted>lisa@beispiel.de</Td>
    <Td>{{ t('helpCenter.sample.stations.dlrgNeustadt') }}</Td>
    <Td muted>12.05.2026</Td>
    <Td>
      <SecondaryBadge>{{ t('adminApplications.pendingBadge') }}</SecondaryBadge>
    </Td>
    <Td align="right">
      <ButtonRow pair align="end">
        <PrimaryButton>{{ t('adminApplications.accept') }}</PrimaryButton>
        <ErrorButton>{{ t('adminApplications.deny') }}</ErrorButton>
      </ButtonRow>
    </Td>
  </TRow>
  <TRow>
    <Td>
      <div class="font-medium">{{ t('helpCenter.sample.people.tomMuster') }}</div>
    </Td>
    <Td muted>tom@muster.de</Td>
    <Td>{{ t('helpCenter.sample.stations.dlrgAltstadt') }}</Td>
    <Td muted>10.05.2026</Td>
    <Td>
      <SuccessBadge>{{ t('adminApplications.accepted') }}</SuccessBadge>
    </Td>
    <Td/>
  </TRow>
  <TRow>
    <Td>
      <div class="font-medium">{{ t('helpCenter.sample.people.annaSchmidt') }}</div>
    </Td>
    <Td muted>anna@schmidt.de</Td>
    <Td>{{ t('helpCenter.sample.stations.dlrgWesthafen') }}</Td>
    <Td muted>08.05.2026</Td>
    <Td>
      <ErrorBadge>{{ t('adminApplications.denied') }}</ErrorBadge>
    </Td>
    <Td>
      <div class="text-xs text-(--text-muted)">{{ t('helpCenter.sample.admin.duplicateApplication') }}</div>
    </Td>
  </TRow>
  </tbody>
</template>
