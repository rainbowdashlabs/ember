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
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3">
    <span class="font-semibold">{{ t('stationPages.createPage') }}</span>
    <div>
      <FormLabel>{{ t('stationPages.editor.titleLabel') }}</FormLabel>
      <TextInput :model-value="t('helpCenter.pages.dummyNewTitle')" :placeholder="t('stationPages.editor.titlePlaceholder')"/>
    </div>
    <div>
      <FormLabel>{{ t('stationPages.editor.parent') }}</FormLabel>
      <SelectInput :model-value="''">
        <option value="">{{ t('stationPages.editor.noParent') }}</option>
      </SelectInput>
    </div>
    <div class="flex justify-end gap-3">
      <SecondaryButton>{{ t('common.cancel') }}</SecondaryButton>
      <PrimaryButton>{{ t('common.create') }}</PrimaryButton>
    </div>
  </NeutralContainer>
</template>
