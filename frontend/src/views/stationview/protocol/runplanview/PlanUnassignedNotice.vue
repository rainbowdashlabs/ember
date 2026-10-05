/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import Alert from '@/components/feedback/Alert.vue'
import type {TestProtocolSection} from '@/api/generated/schema'

/** Names the sections nobody examines, which every examiner of the run may then grade. */
defineProps<{
  sections: TestProtocolSection[]
}>()

const {t} = useI18n()
</script>

<template>
  <Alert v-if="sections.length > 0" variant="info">
    {{ t('protocol.plan.unassigned', {names: sections.map(section => section.name).join(', ')}) }}
  </Alert>
</template>
