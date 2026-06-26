/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import DummyRequirementGroup from '@/views/helpcenter/stationview/inventory/requirementshelp/DummyRequirementGroup.vue'
import DummyRequirementRow from '@/views/helpcenter/stationview/inventory/requirementshelp/DummyRequirementRow.vue'

const {t} = useI18n()
</script>

<template>
  <div class="space-y-4">
    <div class="flex items-center justify-between">
      <SectionHeader>{{ t('inventory.requirements.title') }}</SectionHeader>
      <PrimaryButton :icon="['fas', 'plus']">
        {{ t('inventory.requirements.add') }}
      </PrimaryButton>
    </div>

    <DummyRequirementGroup :category="t('inventory.requirements.userType')" :name="t('membersCreate.roleMember')">
      <DummyRequirementRow name="Helme" :quantity="1"/>
      <DummyRequirementRow name="Jacken" :quantity="1"/>
    </DummyRequirementGroup>

    <DummyRequirementGroup :category="t('inventory.requirements.group')" name="Anfänger">
      <DummyRequirementRow name="Stiefel" :quantity="1"/>
    </DummyRequirementGroup>
  </div>
</template>
