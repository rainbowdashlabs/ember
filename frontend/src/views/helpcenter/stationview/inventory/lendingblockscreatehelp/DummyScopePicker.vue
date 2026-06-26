/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import DummyScopeEntry from '@/views/helpcenter/stationview/inventory/lendingblockscreatehelp/DummyScopeEntry.vue'

const {t} = useI18n()
</script>

<template>
  <div>
    <MutedText tag="div" size="sm" class="mb-3">{{ t('helpCenter.lendingBlocksCreate.scopeHint') }}</MutedText>

    <DummyScopeEntry title="Helme" :all-items="true"/>

    <DummyScopeEntry title="Jacken" :all-items="false">
      <div class="mt-3 flex flex-wrap gap-2">
        <FieldLabel inline class="cursor-pointer">
          <CheckboxInput :model-value="true" disabled />
          Jacke (INV-0010)
        </FieldLabel>
        <FieldLabel inline class="cursor-pointer">
          <CheckboxInput :model-value="true" disabled />
          Jacke (INV-0011)
        </FieldLabel>
        <FieldLabel inline class="cursor-pointer">
          <CheckboxInput :model-value="false" disabled />
          Jacke (INV-0012)
        </FieldLabel>
      </div>
    </DummyScopeEntry>

    <div class="flex gap-2 items-end">
      <div class="flex-1">
        <SelectInput :model-value="''">
          <option value="">{{ t('lending.selectInventory') }}</option>
          <option>Stiefel</option>
        </SelectInput>
      </div>
      <SecondaryButton :icon="['fas', 'plus']" disabled>
        {{ t('lending.addInventory') }}
      </SecondaryButton>
    </div>
  </div>
</template>
