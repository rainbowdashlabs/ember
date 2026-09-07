/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'

/** The tidying screen as it looks with one word written two ways, which is the case it exists for. */
const {t} = useI18n()
</script>

<template>
  <div class="space-y-3">
    <NeutralContainer class="space-y-3">
      <ul class="divide-y divide-(--border)">
        <li class="flex items-center gap-3 py-2">
          <CheckboxInput :model-value="true"/>
          <div>
            <p class="text-sm">Funkgerät orange</p>
            <p class="text-xs text-(--text-muted)">
              {{ t('inventory.art.nameCount', {pieces: 4, unassigned: 4}) }}
            </p>
          </div>
        </li>
        <li class="flex items-center gap-3 py-2">
          <CheckboxInput :model-value="true"/>
          <div>
            <p class="text-sm">Funkgerät organge</p>
            <p class="text-xs text-(--text-muted)">
              {{ t('inventory.art.nameCount', {pieces: 1, unassigned: 1}) }}
            </p>
          </div>
        </li>
        <li class="flex items-center gap-3 py-2">
          <CheckboxInput :model-value="false"/>
          <div>
            <p class="text-sm">Ladestation</p>
            <p class="text-xs text-(--text-muted)">
              {{ t('inventory.art.nameCount', {pieces: 1, unassigned: 1}) }}
            </p>
          </div>
        </li>
      </ul>
    </NeutralContainer>

    <NeutralContainer class="space-y-3">
      <div class="space-y-1">
        <FieldLabel>{{ t('inventory.art.existing') }}</FieldLabel>
        <SelectInput disabled model-value="">
          <option value="">{{ t('inventory.art.newOne') }}</option>
        </SelectInput>
      </div>
      <div class="space-y-1">
        <FieldLabel>{{ t('inventory.art.newName') }}</FieldLabel>
        <TextInput model-value="Funkgerät orange"/>
      </div>
      <p class="text-sm">{{ t('inventory.art.willRename', {count: 5, name: 'Funkgerät orange'}) }}</p>
      <div class="flex flex-wrap gap-2">
        <PrimaryButton :icon="['fas', 'broom']">{{ t('inventory.art.mergeSubmit') }}</PrimaryButton>
        <SecondaryButton :icon="['fas', 'tags']">{{ t('inventory.art.assignSubmit') }}</SecondaryButton>
      </div>
    </NeutralContainer>
  </div>
</template>
