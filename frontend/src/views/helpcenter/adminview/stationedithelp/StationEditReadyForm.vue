/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SuccessContainer from '@/components/container/SuccessContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SectionHeader>{{ t('adminStations.editTitle') }}</SectionHeader>

    <div class="space-y-1">
      <FieldLabel>{{ t('adminStations.name') }}</FieldLabel>
      <TextInput :model-value="'DLRG Musterstadt'" :placeholder="t('adminStations.namePlaceholder')" />
    </div>

    <div class="space-y-2">
      <FieldLabel>{{ t('adminStations.managerEmail') }}</FieldLabel>
      <SuccessContainer class="flex items-center justify-between">
        <div>
          <div class="font-medium">Max Mustermann</div>
          <div class="text-sm text-(--text-muted)">max@mustermann.de</div>
          <div class="text-xs mt-1">
            <span class="text-success">
              <font-awesome-icon :icon="['fas', 'check']" class="mr-1" />{{ t('adminStations.accountReady') }}
            </span>
          </div>
        </div>
        <EditButton />
      </SuccessContainer>
    </div>

    <PrimaryButton>{{ t('adminStations.save') }}</PrimaryButton>
  </NeutralContainer>
</template>
