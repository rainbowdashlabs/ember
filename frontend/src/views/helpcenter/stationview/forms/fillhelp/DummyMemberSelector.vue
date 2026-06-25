/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import InfoContainer from '@/components/container/InfoContainer.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import FieldHint from '@/components/typography/FieldHint.vue'

const {t} = useI18n()
</script>

<template>
  <InfoContainer class="mb-4">
    <div class="space-y-2">
      <FieldHint>{{ t('forms.fillForWhom') }}</FieldHint>
      <SelectInput model-value="self" disabled>
        <option value="self">{{ t('forms.fillForSelfDefault') }}</option>
        <option value="max">Max Mustermann</option>
      </SelectInput>
    </div>
  </InfoContainer>
</template>
