/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import ToggleSetting from '@/components/input/toggle/ToggleSetting.vue'
import {fromCompletion, type MemberOption} from '@/components/input/select/memberOption'
import {documentTemplates, stationMembers} from '@/api'
import {audienceLists} from '../generation'
import type {
  BulkPreviewResponse,
  DocumentTemplateSummary,
  GroupEntry,
  IssuerChoice,
  PreviewIssuer,
  TagEntry,
} from '@/api/generated/schema'
import type {RestrictionSelection} from '@/api/types'
import {emptyRestriction} from '@/components/input/restriction'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {showToast} from '@/util/toast'
import IssuerOverride from '../IssuerOverride.vue'
import {recentlyUsedFirst} from '../recentlyUsedFirst'
import {selectionFor} from './bulkGeneration'
import BulkMemberChoice from './BulkMemberChoice.vue'
import BulkPreviewSummary from './BulkPreviewSummary.vue'
import TemplateChoice from '../TemplateChoice.vue'

/**
 * Generates one template for many members in a background run: choose the template and the members
 * (the ones chosen in the member list, or an audience of groups, user types and tags), look at the
 * document of the first member with the data every member still lacks, decide whether gaps are filed
 * as lines to fill in by hand, and start. The run goes on without the screen; its progress and results
 * stand in the document store. The templates used most recently at the station come first.
 *
 * <p>Where the document names its issuer, the first look shows the template's issuer, and another
 * current member of the station can be picked for the whole run; a new pick asks for a new look.
 */
const open = defineModel<boolean>({required: true})

const props = defineProps<{
  /** The members chosen in the member list, or none to choose by audience. */
  memberIds?: number[]
}>()

const emit = defineEmits<{
  started: [jobId: number]
}>()

const {t} = useI18n()

const templates = ref<DocumentTemplateSummary[]>([])
const groups = ref<GroupEntry[]>([])
const tags = ref<TagEntry[]>([])
const members = ref<MemberOption[]>([])
const templateId = ref<number | null>(null)
const audience = ref<RestrictionSelection>(emptyRestriction())
const acceptMissing = ref(false)
const preview = ref<BulkPreviewResponse | null>(null)
const templateIssuer = ref<PreviewIssuer | null>(null)
const issuer = ref<IssuerChoice | null>(null)

const chosen = computed(() => props.memberIds ?? null)
const selection = computed(() => selectionFor(chosen.value, audience.value))
const templateName = computed(() => templates.value.find(template => template.id === templateId.value)?.name ?? '')

const loader = useAsyncLoader(async () => {
  const [usable, lists, completions] = await Promise.all([
    documentTemplates.usableTemplates(),
    audienceLists(),
    stationMembers.listCompletions().catch(() => []),
  ])
  templates.value = recentlyUsedFirst(usable)
  groups.value = lists.groups
  tags.value = lists.tags
  members.value = completions.map(fromCompletion)
})

const looking = useAsyncAction(async () => {
  if (templateId.value === null) return
  preview.value = await documentTemplates.previewJob(templateId.value, {...selection.value, issuer: issuer.value})
  if (issuer.value === null) templateIssuer.value = preview.value.preview?.issuer ?? null
})

const starting = useAsyncAction(async () => {
  if (templateId.value === null) return
  const started = await documentTemplates.startJob(templateId.value, {
    ...selection.value,
    acceptMissing: acceptMissing.value,
    issuer: issuer.value,
  })
  showToast(t('documentTemplates.bulk.started', {count: started.job.total}), 'success')
  emit('started', started.job.id)
  open.value = false
})

watch(templateId, () => {
  templateIssuer.value = null
  issuer.value = null
})

watch([templateId, audience, issuer], () => {
  preview.value = null
}, {deep: true})
</script>

<template>
  <Modal v-model="open" size="2xl">
    <div class="space-y-4" data-testid="bulk-generate-modal">
      <SubHeader>{{ t('documentTemplates.bulk.title') }}</SubHeader>
      <FailureAlert :failure="loader.failure.value ?? looking.failure.value ?? starting.failure.value"/>
      <TemplateChoice v-model="templateId" :templates="templates" :loading="loader.loading.value"/>
      <BulkMemberChoice v-model="audience" :member-ids="chosen" :groups="groups" :tags="tags"/>
      <IssuerOverride v-if="templateIssuer" :key="templateId ?? 0" v-model="issuer" :template-issuer="templateIssuer"
                      :members="members"/>
      <BulkPreviewSummary v-if="preview" :preview="preview" :template-name="templateName"/>
      <ToggleSetting v-model="acceptMissing" :label="t('documentTemplates.bulk.acceptMissing')"
                     :hint="t('documentTemplates.bulk.acceptMissingHint')" data-testid="bulk-accept-missing"/>
      <ButtonRow align="end">
        <SecondaryButton @click="open = false">{{ t('common.cancel') }}</SecondaryButton>
        <SecondaryButton :icon="['fas', 'eye']" :disabled="templateId === null || looking.running.value"
                         data-testid="bulk-preview" @click="looking.run()">
          {{ t('common.preview') }}
        </SecondaryButton>
        <PrimaryButton :icon="['fas', 'file-circle-plus']" :disabled="!preview || starting.running.value"
                       data-testid="bulk-start" @click="starting.run()">
          {{ t('documentTemplates.bulk.start') }}
        </PrimaryButton>
      </ButtonRow>
    </div>
  </Modal>
</template>
