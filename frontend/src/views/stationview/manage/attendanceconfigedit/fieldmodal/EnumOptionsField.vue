/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'

const options = defineModel<string>({required: true})

const {t} = useI18n()
</script>

<template>
  <div class="space-y-1">
    <FieldLabel>{{ t('attendanceConfig.fieldEnumOptions') }}</FieldLabel>
    <TextInput v-model="options" :placeholder="t('attendanceConfig.fieldEnumOptionsPlaceholder')"/>
    <p class="text-xs text-(--text-muted)">{{ t('attendanceConfig.fieldEnumOptionsHint') }}</p>
  </div>
</template>
