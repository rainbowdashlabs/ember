/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import DummyFieldsTable from './DummyFieldsTable.vue'

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-4">
    <div class="flex items-center justify-between">
      <SectionHeader>{{ t('membersConfig.fields') }}</SectionHeader>
      <PrimaryButton :icon="['fas', 'plus']" disabled>
        {{ t('membersConfig.addField') }}
      </PrimaryButton>
    </div>
    <p class="text-sm text-(--text-muted)">{{ t('membersConfig.memberHint') }}</p>
    <DummyFieldsTable/>
  </NeutralContainer>
</template>
