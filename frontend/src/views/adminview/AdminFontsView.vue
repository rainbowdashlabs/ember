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
import {instanceFontSource} from '@/api/documentFonts'

/** The fonts the instance offers the document templates of every station. */
const {t} = useI18n()
</script>

<template>
  <ViewContent :title="t('pages.admin-document-fonts.title')" :subtitle="t('pages.admin-document-fonts.subtitle')">
    <FontsScreen :source="instanceFontSource" :origin="FontOrigin.INSTANCE"/>
  </ViewContent>
</template>
