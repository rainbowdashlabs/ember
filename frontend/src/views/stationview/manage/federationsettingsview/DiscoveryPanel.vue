/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'

const visibility = defineModel<string>('visibility', {required: true})
const description = defineModel<string>('description', {required: true})

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SubHeader>{{ t('discovery.settings.visibility') }}</SubHeader>
    <SelectInput v-model="visibility" class="w-full">
      <option value="NONE">{{ t('discovery.settings.visibilityNone') }}</option>
      <option value="INSTANCE">{{ t('discovery.settings.visibilityInstance') }}</option>
      <option value="PUBLIC">{{ t('discovery.settings.visibilityPublic') }}</option>
    </SelectInput>

    <div v-if="visibility !== 'NONE'" class="space-y-3">
      <div class="space-y-1">
        <FieldLabel>{{ t('discovery.settings.description') }}</FieldLabel>
        <TextInput v-model="description" :placeholder="t('discovery.settings.descriptionPlaceholder')"/>
      </div>
    </div>
  </NeutralContainer>
</template>
