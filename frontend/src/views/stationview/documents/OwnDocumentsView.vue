/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import ReaderDocuments from '@/components/documents/ReaderDocuments.vue'
import {documentOpeningOf} from '@/components/documents/documentOpening'

/**
 * The self service centre: the reader's own documents and those of each child, each with the
 * documents the reader may create for that member from the station's templates.
 *
 * <p>A link from a signer's copy mail names a document (`?document=`) and maybe its record
 * (`&record=`): that document opens, and its record's download takes the focus.
 */
const {t} = useI18n()
const route = useRoute()

const opening = computed(() => documentOpeningOf(route.query))
</script>

<template>
  <ViewContent :title="t('pages.documents-own.title')" :subtitle="t('pages.documents-own.subtitle')">
    <ReaderDocuments offers :opening="opening"/>
  </ViewContent>
</template>
