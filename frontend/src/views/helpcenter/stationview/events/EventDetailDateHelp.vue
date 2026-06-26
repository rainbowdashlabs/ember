/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
/**
 * Help article for the date-bound event detail route ({@code /events/:id/:date}). Same
 * content as {@link EventDetailHelp} — kept as a separate component file so the help-center
 * linter can pair both app routes (one-off `event-detail` and the date-bound
 * `event-detail-date`) with their own help entry without tripping the duplicate-component
 * check.
 */
import EventDetailHelp from './EventDetailHelp.vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>

<template>
  <EventDetailHelp />
</template>
