/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-4">
    <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
      <div>
        <FieldLabel class="mb-1">{{ t('lending.blockFrom') }}</FieldLabel>
        <DateInput model-value="2026-06-20" disabled />
      </div>
      <div>
        <FieldLabel class="mb-1">{{ t('lending.blockTo') }}</FieldLabel>
        <DateInput model-value="2026-06-22" disabled />
      </div>
    </div>
    <div>
      <FieldLabel class="mb-1">{{ t('lending.blockReason') }}</FieldLabel>
      <TextInput model-value="Stadtfest" disabled />
    </div>
  </NeutralContainer>
</template>
