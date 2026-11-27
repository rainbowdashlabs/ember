/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SettingToggle from './SettingToggle.vue'

/**
 * What the question defines about itself, which holds for everybody asked it. Who may write the answer is
 * one of them: it is the question's own and not something one audience gets and another does not.
 */
const required = defineModel<boolean>('required', {required: true})
const readonly = defineModel<boolean>('readonly', {required: true})
const notifyOnChange = defineModel<boolean>('notifyOnChange', {required: true})
const overview = defineModel<boolean>('overview', {required: true})
const keepOnArchive = defineModel<boolean>('keepOnArchive', {required: true})

/**
 * Whether the answer is worked out rather than given.
 *
 * <p>A worked out answer is nobody's to write, so expecting one, locking one and reporting a change
 * to one are all questions about something that cannot happen. They are left out rather than shown
 * switched off, because a switch that does nothing still reads as a choice.
 */
const props = withDefaults(defineProps<{calculated?: boolean}>(), {calculated: false})

const {t} = useI18n()
</script>

<template>
  <div class="space-y-4">
    <template v-if="!props.calculated">
      <SettingToggle v-model="required" :label="t('membersConfig.fieldRequired')"
                     :hint="t('membersConfig.fieldRequiredHint')"/>
      <SettingToggle v-model="readonly" :label="t('membersConfig.fieldReadonly')"
                     :hint="t('membersConfig.fieldReadonlyHint')"/>
      <SettingToggle v-model="notifyOnChange" :label="t('membersConfig.fieldNotifyOnChange')"
                     :hint="t('membersConfig.fieldNotifyOnChangeHint')"/>
    </template>
    <SettingToggle v-model="overview" :label="t('membersConfig.fieldOverview')"/>
    <SettingToggle v-model="keepOnArchive" :label="t('membersConfig.fieldKeepOnArchive')"
                   :hint="t('membersConfig.fieldKeepOnArchiveHint')"/>
  </div>
</template>
