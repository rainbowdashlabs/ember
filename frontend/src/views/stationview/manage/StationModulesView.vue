/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import StationModuleList from './stationmodulesview/StationModuleList.vue'
import {stationManage} from '@/api'
import {StationPermission} from '@/api/types'
import type {ModulesResponse, StationModule} from '@/api/generated/schema'
import {useSession} from '@/composables/useSession'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useAsyncLoader} from '@/composables/useAsyncLoader'

const {hasPermission, loaded, load: reloadSession} = useSession()
const router = useRouter()
watch(loaded, (isLoaded) => {
  if (isLoaded && !hasPermission(StationPermission.STATION_MODULES)) {
    router.replace('/station/dashboard/overview')
  }
}, {immediate: true})

const {t} = useI18n()

const disabledModules = ref<Set<StationModule>>(new Set())
const clusterDenied = ref<Set<StationModule>>(new Set())
const clusterName = ref<string | null>(null)

const {running: modulesSaving, failure, run: toggleModule} = useAsyncAction(async (key: StationModule) => {
  const next = new Set(disabledModules.value)
  if (next.has(key)) next.delete(key)
  else next.add(key)
  const res = await stationManage.setDisabledModules([...next])
  apply(res)
  reloadSession()
})

function apply(res: ModulesResponse) {
  disabledModules.value = new Set(res.disabledModules)
  clusterDenied.value = new Set(res.clusterDeniedModules)
  clusterName.value = res.clusterName
}

const {loading, failure: loadFailure} = useAsyncLoader(async () => {
  apply(await stationManage.getDisabledModules())
})
</script>

<template>
  <ViewContent
      :title="t('pages.station-modules.title')"
      :subtitle="t('pages.station-modules.subtitle')"
  >
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <FailureAlert :failure="loadFailure"/>
      <FailureAlert :failure="failure"/>

      <StationModuleList v-if="!loading" :disabled-modules="disabledModules" :cluster-denied="clusterDenied"
                         :cluster-name="clusterName" :saving="modulesSaving" @toggle="toggleModule"/>
    </div>
  </ViewContent>
</template>
