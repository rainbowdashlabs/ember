/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import DummyAvailableItem from './DummyAvailableItem.vue'

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-4">
    <div class="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-2">
      <SectionHeader>{{ t('helpCenter.inventoryLending.title') }}</SectionHeader>
      <PrimaryButton :icon="['fas', 'calendar-xmark']">
        {{ t('helpCenter.inventoryLending.dummyBlocks') }}
      </PrimaryButton>
    </div>

    <div class="flex gap-2">
      <SelectionToggleButton :selected="true">
        <font-awesome-icon :icon="['fas', 'boxes-stacked']" class="mr-1"/>
        {{ t('helpCenter.inventoryLending.tabOffers') }}
      </SelectionToggleButton>
      <SelectionToggleButton :selected="false">
        <font-awesome-icon :icon="['fas', 'list']" class="mr-1"/>
        {{ t('helpCenter.inventoryLending.tabRequests') }}
      </SelectionToggleButton>
    </div>

    <TextInput :model-value="''" :placeholder="t('helpCenter.inventoryLending.dummySearch')" disabled/>
    <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
      <div>
        <FieldLabel class="mb-1">{{ t('helpCenter.inventoryLending.dummyDateFrom') }}</FieldLabel>
        <DateInput model-value="" disabled/>
      </div>
      <div>
        <FieldLabel class="mb-1">{{ t('helpCenter.inventoryLending.dummyDateTo') }}</FieldLabel>
        <DateInput model-value="" disabled/>
      </div>
    </div>

    <SubHeader>{{ t('helpCenter.inventoryLending.dummyAvailable') }}</SubHeader>
    <DummyAvailableItem name="Rettungsringe" station="DLRG Musterstadt" :count="5"/>
    <DummyAvailableItem name="Taucheranzüge" station="DLRG Beispielburg" :count="3"/>
  </NeutralContainer>
</template>
