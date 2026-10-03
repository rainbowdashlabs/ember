/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import ReaderDocuments from '@/components/documents/ReaderDocuments.vue'

/**
 * The self service centre: the reader's own documents and those of each child, each with the
 * documents the reader may create for that member from the station's templates.
 */
const {t} = useI18n()
</script>

<template>
  <ViewContent :title="t('pages.documents-own.title')" :subtitle="t('pages.documents-own.subtitle')">
    <ReaderDocuments offers/>
  </ViewContent>
</template>
