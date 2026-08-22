/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {useFieldsCapabilities} from '@/composables/useFieldsConfig'

const {t} = useI18n()

const capabilities = useFieldsCapabilities()
</script>

<template>
  <div :title="t('membersConfig.fieldRequired')" class="text-center">
    <font-awesome-icon :icon="['fas', 'asterisk']" class="h-3 w-3 text-error"/>
  </div>
  <div v-if="capabilities.writability" :title="t('membersConfig.writability.hint')"
       class="text-center text-xs font-medium">
    {{ t('membersConfig.writability.column') }}
  </div>
  <div v-else :title="t('membersConfig.fieldReadonly')" class="text-center">
    <font-awesome-icon :icon="['fas', 'lock']" class="h-3 w-3"/>
  </div>
  <div :title="t('membersConfig.fieldNotifyOnChange')" class="text-center">
    <font-awesome-icon :icon="['fas', 'bell']" class="h-3 w-3"/>
  </div>
  <div :title="t('membersConfig.fieldOverview')" class="text-center">
    <font-awesome-icon :icon="['fas', 'list']" class="h-3 w-3"/>
  </div>
  <div :title="t('membersConfig.fieldKeepOnArchive')" class="text-center">
    <font-awesome-icon :icon="['fas', 'user-slash']" class="h-3 w-3"/>
  </div>
</template>
