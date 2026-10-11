/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import ReaderPersonTabs from '@/components/documents/ReaderPersonTabs.vue'
import SelfServiceDocuments from '@/components/documents/SelfServiceDocuments.vue'
import {useReaderPersonChoice} from '@/components/documents/readerPersonChoice'
import {useReaderPeople} from '@/composables/useReaderPeople'

/**
 * Creating documents for oneself: the documents the station's templates offer the reader, and a
 * guardian those for each child, one person at a time. It has a page of its own beside the reader's
 * documents, so neither page carries both a list to search and a list of things to create.
 */
const {t} = useI18n()
const people = useReaderPeople()
const {selected} = useReaderPersonChoice(people)
</script>

<template>
  <ViewContent :title="t('pages.documents-self-service.title')" :subtitle="t('pages.documents-self-service.subtitle')">
    <div class="space-y-4">
      <ReaderPersonTabs v-model="selected" :people="people"/>
      <SelfServiceDocuments v-if="selected !== null" :key="selected" :member-id="selected" :people="people"/>
    </div>
  </ViewContent>
</template>
