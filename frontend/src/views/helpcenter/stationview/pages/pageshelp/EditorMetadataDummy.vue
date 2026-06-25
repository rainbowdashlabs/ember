/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3">
    <span class="font-semibold">{{ t('stationPages.editor.metadata') }}</span>
    <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
      <div>
        <FormLabel>{{ t('stationPages.editor.titleLabel') }}</FormLabel>
        <TextInput :model-value="t('helpCenter.pages.dummyPage1')" :placeholder="t('stationPages.editor.titlePlaceholder')"/>
      </div>
      <div>
        <FormLabel>{{ t('stationPages.editor.slugLabel') }}</FormLabel>
        <TextInput :model-value="t('helpCenter.pages.dummySlug1')" :placeholder="t('stationPages.editor.slugPlaceholder')"/>
      </div>
      <div>
        <FormLabel>{{ t('stationPages.editor.parent') }}</FormLabel>
        <SelectInput :model-value="''">
          <option value="">{{ t('stationPages.editor.noParent') }}</option>
        </SelectInput>
      </div>
      <div>
        <FormLabel>{{ t('stationPages.editor.metaDescription') }}</FormLabel>
        <TextInput :model-value="t('helpCenter.pages.dummyMetaDesc')" :placeholder="t('stationPages.editor.metaDescriptionPlaceholder')"/>
      </div>
    </div>
  </NeutralContainer>
</template>
