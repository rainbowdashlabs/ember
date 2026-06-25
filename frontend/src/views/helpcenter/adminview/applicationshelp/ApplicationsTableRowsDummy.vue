/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
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
      <div class="font-medium">Lisa Beispiel</div>
      <div class="text-xs text-(--text-muted) mt-0.5">Wir möchten unsere Wache digital verwalten.</div>
    </Td>
    <Td muted>lisa@beispiel.de</Td>
    <Td>DLRG Neustadt</Td>
    <Td muted>12.05.2026</Td>
    <Td>
      <SecondaryBadge>{{ t('adminApplications.pendingBadge') }}</SecondaryBadge>
    </Td>
    <Td align="right">
      <div class="flex items-center justify-end gap-1">
        <PrimaryButton>{{ t('adminApplications.accept') }}</PrimaryButton>
        <ErrorButton>{{ t('adminApplications.deny') }}</ErrorButton>
      </div>
    </Td>
  </TRow>
  <TRow>
    <Td>
      <div class="font-medium">Tom Muster</div>
    </Td>
    <Td muted>tom@muster.de</Td>
    <Td>DLRG Altstadt</Td>
    <Td muted>10.05.2026</Td>
    <Td>
      <SuccessBadge>{{ t('adminApplications.accepted') }}</SuccessBadge>
    </Td>
    <Td></Td>
  </TRow>
  <TRow>
    <Td>
      <div class="font-medium">Anna Schmidt</div>
    </Td>
    <Td muted>anna@schmidt.de</Td>
    <Td>DLRG Westhafen</Td>
    <Td muted>08.05.2026</Td>
    <Td>
      <ErrorBadge>{{ t('adminApplications.denied') }}</ErrorBadge>
    </Td>
    <Td>
      <div class="text-xs text-(--text-muted)">Doppelter Antrag</div>
    </Td>
  </TRow>
  </tbody>
</template>
