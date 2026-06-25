/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'

const blockFrom = defineModel<string>('blockFrom', {required: true})
const blockTo = defineModel<string>('blockTo', {required: true})
const reason = defineModel<string>('reason', {required: true})

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-4">
    <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
      <div>
        <FieldLabel class="mb-1">{{ t('lending.blockFrom') }}</FieldLabel>
        <DateInput v-model="blockFrom"/>
      </div>
      <div>
        <FieldLabel class="mb-1">{{ t('lending.blockTo') }}</FieldLabel>
        <DateInput v-model="blockTo"/>
      </div>
    </div>
    <div>
      <FieldLabel class="mb-1">{{ t('lending.blockReason') }}</FieldLabel>
      <TextInput v-model="reason" :placeholder="t('lending.blockReasonPlaceholder')"/>
    </div>
  </NeutralContainer>
</template>
