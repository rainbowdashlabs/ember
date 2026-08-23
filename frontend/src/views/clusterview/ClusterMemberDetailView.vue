/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ProfileFieldsLayout, {type LaidOutField} from '@/components/profilefields/ProfileFieldsLayout.vue'
import MemberDocumentsPanel from './clustermemberdetailview/MemberDocumentsPanel.vue'
import {clusterMembers} from '@/api'
import type {ManagedMemberProfile, ManagedProfileValue} from '@/api/clusterMembers'
import {ClusterPermission} from '@/api/clusters'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useSession} from '@/composables/useSession'

/**
 * One person at one of the association's stations, and everything asked of them.
 *
 * <p>The station's own questions and the association's are laid out together, each keeping the origin
 * it arrived with so the answer goes back to the right table. A question the association marked
 * readable but not writable at the station is still writable here: this screen is the association's,
 * and it is the party that put the lock there.
 */
const {t} = useI18n()
const route = useRoute()
const {hasClusterPermission} = useSession()

const memberId = computed(() => Number(route.params.id))
const editable = computed(() => hasClusterPermission(ClusterPermission.CLUSTER_MEMBER_MANAGER))

const profile = ref<ManagedMemberProfile | null>(null)
const edited = ref<Map<number, string>>(new Map())
const saved = ref(false)

const {loading, error, reload} = useAsyncLoader(async () => {
  profile.value = await clusterMembers.getManagedMemberProfile(memberId.value)
  edited.value = new Map()
  saved.value = false
})

const fields = computed<LaidOutField[]>(() => (profile.value?.fields ?? []).map(f => ({
  id: f.id,
  name: f.name,
  fieldType: f.fieldType,
  config: f.config,
  position: f.position,
  scope: f.scope,
})))

/** Which table a question lives in, kept beside the laid out field rather than inside it. */
const originOf = computed(() => new Map((profile.value?.fields ?? []).map(f => [f.id, f.origin])))

const storedValues = computed(() => new Map((profile.value?.values ?? []).map(v => [v.fieldId, v.value])))

/**
 * An answer travels as JSON and is shown as itself.
 *
 * <p>A stored answer is JSON text, so a name arrives wrapped in quotation marks. Handing that
 * straight to the form would put the quotation marks in front of the reader, which is what happened
 * until a story looked at the screen rather than at the database.
 */
function decode(raw: string | undefined): string {
  if (raw === undefined || raw === '') return ''
  try {
    const parsed: unknown = JSON.parse(raw)
    return parsed === null ? '' : String(parsed)
  } catch {
    return raw
  }
}

function valueOf(field: LaidOutField): string {
  const pending = edited.value.get(field.id)
  if (pending !== undefined) return pending
  return decode(storedValues.value.get(field.id))
}

function onUpdate(field: LaidOutField, value: string) {
  edited.value = new Map([...edited.value, [field.id, value]])
  saved.value = false
}

const {running: saving, error: saveError, run: save} = useAsyncAction(async () => {
  // Back out the way it came in, as JSON, which is what both tables behind this store.
  const values: ManagedProfileValue[] = [...edited.value.entries()].map(([fieldId, value]) => ({
    fieldId,
    value: JSON.stringify(value),
    origin: originOf.value.get(fieldId) ?? 'STATION',
  }))
  if (values.length === 0) return
  await clusterMembers.setManagedMemberProfile(memberId.value, values)
  await reload()
  saved.value = true
}, {formatError: () => t('common.error')})
</script>

<template>
  <ViewContent :subtitle="profile?.name ?? ''" :title="t('pages.cluster-member-detail.title')">
    <div class="space-y-4">
      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error || saveError" variant="error">{{ error || saveError }}</Alert>
      <Alert v-if="saved" variant="success">{{ t('clusterMemberDetail.saved') }}</Alert>

      <NeutralContainer v-if="!loading && profile" class="space-y-4">
        <div class="flex items-center justify-between gap-3">
          <SectionHeader>{{ t('clusterMemberDetail.fieldsTitle') }}</SectionHeader>
          <SecondaryBadge>{{ t('clusterMemberDetail.fieldCount', {count: fields.length}) }}</SecondaryBadge>
        </div>

        <p class="text-sm text-(--text-muted)">{{ t('clusterMemberDetail.hint') }}</p>

        <fieldset :disabled="!editable" class="contents">
          <ProfileFieldsLayout :fields="fields" :get-value="valueOf" can-edit-readonly @update="onUpdate"/>
        </fieldset>

        <PrimaryButton v-if="editable" :disabled="saving || edited.size === 0" @click="save">
          {{ saving ? t('common.loading') : t('common.save') }}
        </PrimaryButton>
      </NeutralContainer>

      <MemberDocumentsPanel v-if="!loading && profile" :can-upload="editable" :member-id="memberId"/>
    </div>
  </ViewContent>
</template>
