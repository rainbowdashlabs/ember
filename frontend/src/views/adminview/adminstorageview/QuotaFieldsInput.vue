/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import BaseInput from '@/components/input/BaseInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import {QUOTA_FIELD_KEYS, QUOTA_FIELD_LABELS, SIZE_UNITS, type QuotaFields} from '@/util/storage'

/**
 * The seven dimensions room is measured in, as somebody edits them.
 *
 * <p>One control for both owners: a tier at the instance, and a tier, the defaults or one station's grant at
 * an association. A field left empty means that dimension is not being decided here, which only an
 * association can say.
 */
defineProps<{ fields: QuotaFields }>()

const {t} = useI18n()
</script>

<template>
  <div class="space-y-3">
    <div v-for="key in QUOTA_FIELD_KEYS" :key="key">
      <label class="block text-sm font-medium mb-1">{{ t(QUOTA_FIELD_LABELS[key]) }}</label>
      <div class="flex gap-2">
        <BaseInput v-model="fields[key].value" :data-testid="`quota-field-${key}`" class="flex-1" placeholder="-"
                   step="0.01" type="number"/>
        <SelectInput v-model="fields[key].unit" class="w-24">
          <option v-for="unit in SIZE_UNITS" :key="unit" :value="unit">{{ unit }}</option>
        </SelectInput>
      </div>
    </div>
  </div>
</template>
