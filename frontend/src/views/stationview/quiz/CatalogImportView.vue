/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import {useSession} from '@/composables/useSession'
import ImportErrorAlert from './catalogimportview/ImportErrorAlert.vue'
import ImportHeader from './catalogimportview/ImportHeader.vue'
import ImportSteps from './catalogimportview/ImportSteps.vue'
import {useCatalogImport} from './catalogimportview/useCatalogImport'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const {loaded} = useSession()

const catalogId = computed(() => (route.params.id ? Number(route.params.id) : null))
const wizard = useCatalogImport(() => catalogId.value)

const leaveLabel = computed(() =>
  wizard.appending.value && wizard.catalogName.value ? wizard.catalogName.value : t('quiz.catalogs.title'),
)

watch(loaded, isLoaded => { if (isLoaded) wizard.loadCatalogName() }, {immediate: true})

function leave() {
  if (catalogId.value !== null) router.push({name: 'quiz-catalog-detail', params: {id: catalogId.value}})
  else router.push({name: 'quiz-catalogs'})
}
</script>

<template>
  <ViewContent :title="t('pages.quiz-catalog-import.title')" :subtitle="t('pages.quiz-catalog-import.subtitle')">
    <div class="space-y-6">
      <ImportHeader :leave-label="leaveLabel" :appending="wizard.appending.value" @leave="leave" />
      <ImportErrorAlert :message="wizard.error.value" :problems="wizard.problems.value" />
      <ImportSteps :wizard="wizard" :leave-label="leaveLabel" @leave="leave" />
      <Spinner v-if="wizard.loading.value" size="lg" />
    </div>
  </ViewContent>
</template>
