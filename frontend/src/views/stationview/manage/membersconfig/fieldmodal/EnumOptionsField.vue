/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'

const options = defineModel<string>({required: true})

const {t} = useI18n()
</script>

<template>
  <div class="space-y-1">
    <FieldLabel>{{ t('membersConfig.fieldEnumOptions') }}</FieldLabel>
    <TextAreaInput v-model="options" :placeholder="t('membersConfig.fieldEnumOptionsPlaceholder')"/>
    <p class="text-xs text-(--text-muted)">{{ t('membersConfig.fieldEnumOptionsHint') }}</p>
  </div>
</template>
