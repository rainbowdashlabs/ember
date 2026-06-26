/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Th from '@/components/table/Th.vue'
import THead from '@/components/table/THead.vue'
import ApplicationsTableRowsDummy from './ApplicationsTableRowsDummy.vue'

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="overflow-x-auto">
    <table class="w-full text-sm">
      <thead>
      <THead>
        <Th>{{ t('adminApplications.name') }}</Th>
        <Th>{{ t('adminApplications.email') }}</Th>
        <Th>{{ t('adminApplications.station') }}</Th>
        <Th>{{ t('adminApplications.date') }}</Th>
        <Th>{{ t('adminApplications.status') }}</Th>
        <th class="px-3 py-2"></th>
      </THead>
      </thead>
      <ApplicationsTableRowsDummy />
    </table>
  </NeutralContainer>
</template>
