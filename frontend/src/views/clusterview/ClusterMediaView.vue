/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import MediaView from '@/views/stationview/media/MediaView.vue'

/**
 * The association's media library, which is the station one over the station the association owns.
 *
 * <p>The pictures of the association's letter templates are kept there, beside those of its wiki and
 * news, and are uploaded and organised with the station's own screen.
 */
const {t} = useI18n()
</script>

<template>
  <MediaView :title="t('pages.cluster-media.title')" :subtitle="t('pages.cluster-media.subtitle')"/>
</template>
