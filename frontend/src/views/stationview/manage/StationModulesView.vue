/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import StationModuleToggle from '@/components/modules/StationModuleToggle.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import {STATION_MODULE_OPTIONS} from '@/data/stationModules'
import {stationManage} from '@/api'
import {StationPermission} from '@/api/types'
import {useSession} from '@/composables/useSession'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {describeFailure, type Failure} from '@/util/failure'

const {hasPermission, loaded, load: reloadSession} = useSession()
const router = useRouter()
watch(loaded, (isLoaded) => {
  if (isLoaded && !hasPermission(StationPermission.STATION_MODULES)) {
    router.replace('/station/dashboard/overview')
  }
}, {immediate: true})

const {t} = useI18n()

const loading = ref(true)
const disabledModules = ref<Set<string>>(new Set())
const clusterDenied = ref<Set<string>>(new Set())
const clusterName = ref<string | null>(null)

function isModuleEnabled(key: string): boolean {
  return !disabledModules.value.has(key) && !clusterDenied.value.has(key)
}

/** A module the cluster switched off is shown as locked rather than simply off, and says who locked it. */
function isLockedByCluster(key: string): boolean {
  return clusterDenied.value.has(key)
}

const loadFailure = ref<Failure | null>(null)

const {running: modulesSaving, failure, run: toggleModule} = useAsyncAction(async (key: string) => {
  const next = new Set(disabledModules.value)
  if (next.has(key)) next.delete(key)
  else next.add(key)
  const res = await stationManage.setDisabledModules([...next])
  apply(res)
  reloadSession()
})

function apply(res: stationManage.ModulesResponse) {
  disabledModules.value = new Set(res.disabledModules)
  clusterDenied.value = new Set(res.clusterDeniedModules ?? [])
  clusterName.value = res.clusterName ?? null
}

onMounted(async () => {
  try {
    apply(await stationManage.getDisabledModules())
  } catch (e) {
    loadFailure.value = describeFailure(e, t)
  }
  loading.value = false
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

      <NeutralContainer v-if="!loading" class="space-y-4">
        <SectionHeader>{{ t('stationManage.modulesTitle') }}</SectionHeader>
        <p class="text-sm text-(--text-muted)">{{ t('stationManage.modulesHint') }}</p>
        <div class="space-y-3">
          <StationModuleToggle
              v-for="mod in STATION_MODULE_OPTIONS"
              :key="mod.value"
              :module="mod"
              :model-value="isModuleEnabled(mod.value)"
              :disabled="modulesSaving || isLockedByCluster(mod.value)"
              @update:model-value="toggleModule(mod.value)"
          >
            <span v-if="isLockedByCluster(mod.value)" class="ml-2 text-xs text-(--text-muted)">
              <font-awesome-icon :icon="['fas', 'lock']" class="mr-1 h-3 w-3"/>
              {{ t('stationManage.moduleClusterLocked', {cluster: clusterName ?? ''}) }}
            </span>
          </StationModuleToggle>
        </div>
      </NeutralContainer>
    </div>
  </ViewContent>
</template>
