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
import {associationFontSource} from '@/api/documentFonts'

/** The fonts the association uploads, which the templates of all its stations reach. */
const {t} = useI18n()
</script>

<template>
  <ViewContent :title="t('pages.cluster-document-fonts.title')" :subtitle="t('pages.cluster-document-fonts.subtitle')">
    <FontsScreen :source="associationFontSource" :origin="FontOrigin.ASSOCIATION"/>
  </ViewContent>
</template>
