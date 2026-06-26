/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DummyCheckHeader from '@/views/helpcenter/stationview/inventory/checkmemberhelp/DummyCheckHeader.vue'
import DummyConfirmedSection from '@/views/helpcenter/stationview/inventory/checkmemberhelp/DummyConfirmedSection.vue'
import DummyLostSection from '@/views/helpcenter/stationview/inventory/checkmemberhelp/DummyLostSection.vue'
import DummyEmptySection from '@/views/helpcenter/stationview/inventory/checkmemberhelp/DummyEmptySection.vue'

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-4">
    <DummyCheckHeader />
    <DummyConfirmedSection />
    <DummyLostSection />
    <DummyEmptySection />

    <div class="flex justify-end gap-3">
      <SecondaryButton>{{ t('inventory.check.cancel') }}</SecondaryButton>
      <PrimaryButton>{{ t('inventory.check.complete') }}</PrimaryButton>
    </div>
  </NeutralContainer>
</template>
