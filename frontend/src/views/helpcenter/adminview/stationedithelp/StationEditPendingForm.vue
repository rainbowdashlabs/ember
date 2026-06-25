/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import InfoContainer from '@/components/container/InfoContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-4 mt-4">
    <SectionHeader>{{ t('adminStations.editTitle') }}</SectionHeader>

    <div class="space-y-1">
      <FieldLabel>{{ t('adminStations.name') }}</FieldLabel>
      <TextInput :model-value="'DLRG Beispielburg'" :placeholder="t('adminStations.namePlaceholder')" />
    </div>

    <div class="space-y-2">
      <FieldLabel>{{ t('adminStations.managerEmail') }}</FieldLabel>
      <InfoContainer class="flex items-center justify-between">
        <div>
          <div class="font-medium">neu@manager.de</div>
          <div class="text-sm text-(--text-muted)">neu@manager.de</div>
          <div class="text-xs mt-1">
            <span class="text-info">
              <font-awesome-icon :icon="['fas', 'clock-rotate-left']" class="mr-1" />{{ t('adminStations.accountPending') }}
            </span>
          </div>
        </div>
        <EditButton />
      </InfoContainer>
    </div>

    <PrimaryButton>{{ t('adminStations.save') }}</PrimaryButton>
  </NeutralContainer>
</template>
