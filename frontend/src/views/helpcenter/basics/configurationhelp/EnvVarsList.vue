/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {ENVIRONMENT_GROUPS, rowsOf} from '@/data/environmentVariables'
import EnvVarGroup from './EnvVarGroup.vue'

const {t} = useI18n()

const groups = ENVIRONMENT_GROUPS.map(group => ({
  title: t(group.title),
  note: group.note ? t(group.note) : undefined,
  defaultOpen: group.defaultOpen,
  vars: rowsOf(group).map(row => ({name: row.name, configKey: row.configKey, default: row.default, desc: t(row.descriptionKey)})),
}))
</script>

<template>
  <EnvVarGroup
    v-for="g in groups"
    :key="g.title"
    :title="g.title"
    :note="g.note"
    :vars="g.vars"
    :default-open="g.defaultOpen"
  />
</template>
