/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import DateInput from '@/components/input/datetime/DateInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import FieldHint from '@/components/typography/FieldHint.vue'

const dateFrom = defineModel<string>('dateFrom', {required: true})
const dateTo = defineModel<string>('dateTo', {required: true})

const {t} = useI18n()
</script>

<template>
  <div class="space-y-2">
    <div class="flex flex-wrap items-end gap-3">
      <div>
        <FieldLabel>{{ t('inventory.collections.from') }}</FieldLabel>
        <DateInput v-model="dateFrom" data-testid="collection-from"/>
      </div>
      <div>
        <FieldLabel>{{ t('inventory.collections.to') }}</FieldLabel>
        <DateInput v-model="dateTo" data-testid="collection-to"/>
      </div>
    </div>
    <FieldHint>{{ t('inventory.collections.windowHint') }}</FieldHint>
  </div>
</template>
