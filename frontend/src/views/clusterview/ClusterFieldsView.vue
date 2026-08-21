/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ClusterFieldEditor from './clusterfieldsview/ClusterFieldEditor.vue'
import ClusterFieldRow from './clusterfieldsview/ClusterFieldRow.vue'
import {clusterFields} from '@/api'
import type {ClusterField, ClusterFieldRequest} from '@/api/clusterFields'
import {useConfigPanel} from '@/composables/useConfigPanel'
import {useSession} from '@/composables/useSession'
import {ClusterPermission} from '@/api/clusters'

const {t} = useI18n()
const {hasClusterPermission} = useSession()

const busy = ref(false)
const editing = ref<ClusterField | null>(null)
const adding = ref(false)

const {config: fields, loading, error, runWith} = useConfigPanel<ClusterField[]>({
  initial: [],
  fetch: () => clusterFields.listFields(),
})

const editable = hasClusterPermission(ClusterPermission.CLUSTER_FIELD_EDIT)

async function save(request: ClusterFieldRequest) {
  await runWith(async () => {
    if (editing.value) await clusterFields.updateField(editing.value.id, request)
    else await clusterFields.createField(request)
    editing.value = null
    adding.value = false
    return clusterFields.listFields()
  }, {busy})
}

async function remove(fieldId: number) {
  await runWith(async () => {
    await clusterFields.deleteField(fieldId)
    return clusterFields.listFields()
  }, {busy})
}
</script>

<template>
  <ViewContent :subtitle="t('pages.cluster-fields.subtitle')" :title="t('pages.cluster-fields.title')">
    <div class="space-y-6">
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <p class="text-sm text-(--text-muted)">{{ t('clusterFields.hint') }}</p>

      <NeutralContainer v-if="editable && (adding || editing)" class="space-y-4">
        <SectionHeader>{{ editing ? t('clusterFields.editTitle') : t('clusterFields.addTitle') }}</SectionHeader>
        <ClusterFieldEditor
            :busy="busy"
            :field="editing"
            @cancel="editing = null; adding = false"
            @save="save"
        />
      </NeutralContainer>

      <PrimaryButton v-else-if="editable" :disabled="busy" @click="adding = true">
        {{ t('clusterFields.add') }}
      </PrimaryButton>

      <Spinner v-if="loading" size="lg"/>

      <template v-else>
        <EmptyState v-if="fields.length === 0">{{ t('clusterFields.empty') }}</EmptyState>
        <div v-else class="space-y-2">
          <ClusterFieldRow
              v-for="field in fields"
              :key="field.id"
              :busy="busy"
              :editable="editable"
              :field="field"
              @edit="f => { editing = f; adding = false }"
              @remove="remove"
          />
        </div>
      </template>
    </div>
  </ViewContent>
</template>
