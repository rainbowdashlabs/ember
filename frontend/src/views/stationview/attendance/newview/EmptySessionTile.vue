/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import StartTile from '@/components/start/StartTile.vue'
import StartTileSection from '@/components/start/StartTileSection.vue'

/**
 * The way in for a sheet whose people no template describes.
 *
 * <p>Until this stood here, a station that wanted one kept a template with no groups on it, which
 * then sat in the template list, the report and the configuration screen saying nothing to anybody
 * who found it.
 */
const emit = defineEmits<{
  (e: 'start'): void
}>()

const {t} = useI18n()
</script>

<template>
  <StartTileSection :title="t('attendanceNew.withoutTemplate')">
    <StartTile data-testid="attendance-start-empty" :title="t('attendanceNew.emptyTitle')"
               :button-label="t('attendanceNew.create')" :hint="t('attendanceNew.emptyHint')"
               @start="emit('start')"/>
  </StartTileSection>
</template>
