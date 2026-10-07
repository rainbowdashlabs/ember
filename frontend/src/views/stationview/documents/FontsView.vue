/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import FontsScreen from '@/components/documents/fonts/FontsScreen.vue'
import {FontOrigin} from '@/api/generated/schema'
import {stationFontSource} from '@/api/documentFonts'

/** The fonts the station uploads for its document templates, beside those of its association and the instance. */
const {t} = useI18n()
</script>

<template>
  <ViewContent :title="t('pages.documents-fonts.title')" :subtitle="t('pages.documents-fonts.subtitle')">
    <FontsScreen :source="stationFontSource" :origin="FontOrigin.STATION"/>
  </ViewContent>
</template>
