/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import FormSection from '@/components/container/FormSection.vue'
import DocumentRequirementsEditor from '../eventshared/DocumentRequirementsEditor.vue'
import {documentsSummary} from '../eventshared/sectionSummaries'
import type {useDocumentRequirements} from '../eventshared/useDocumentRequirements'
import AttachmentsCard from '../eventeditview/AttachmentsCard.vue'
import {useEventAttachments} from '../eventeditview/useEventAttachments'
import {eventGroupAttachmentPort} from '../eventeditview/eventOwnerPorts'
import type {RequiredTemplate} from '@/api/generated/schema'
import {useSession} from '@/composables/useSession'

/**
 * The documents an event group asks every part to bring and the files it hands over on every part, in
 * the event group editor. The documents are saved with the event group; the files are written as they
 * change once the event group exists. Files added to a new event group wait until it is created, and
 * the editor hangs them on it through {@link attachPendingTo}. Both sit in one folded section that
 * counts them.
 */
const props = defineProps<{
  eventGroupId: number | null
  requirements: ReturnType<typeof useDocumentRequirements>
  /** Inside the event group's form: explanations behind hint icons, no heading where the control names itself. */
  compact?: boolean
}>()

const {t} = useI18n()
const {sessionInfo} = useSession()
const stationUid = computed(() => sessionInfo.value?.stationId ?? '')
const attachments = useEventAttachments(
    () => (props.eventGroupId == null ? null : eventGroupAttachmentPort(props.eventGroupId)), t)

watch(() => props.eventGroupId, id => {
  if (id != null) attachments.load()
}, {immediate: true})

function choose(chosen: RequiredTemplate[]) {
  props.requirements.choose(chosen)
}

/**
 * Hangs the files added before the event group existed on the one just created, in their order.
 *
 * @param eventGroupId the event group just created
 * @param carried      the library files it already received from its template, which are not attached twice
 * @return the names of the files that could not be attached
 */
function attachPendingTo(eventGroupId: number, carried: ReadonlySet<number> = new Set()): Promise<string[]> {
  return attachments.attachPendingTo(eventGroupAttachmentPort(eventGroupId), carried)
}

defineExpose({attachPendingTo})
</script>

<template>
  <FormSection
      :title="t('formSections.documents')"
      :summary="documentsSummary(props.requirements.chosen.value.length, attachments.attachments.value.length, t)"
      data-testid="form-section-documents"
  >
    <DocumentRequirementsEditor
        :model-value="props.requirements.chosen.value"
        :offered-count="props.requirements.offeredCount.value"
        :hint="t('eventGroups.documentsHint')"
        :compact="props.compact"
        @update:model-value="choose"
    />
    <AttachmentsCard
        v-model:attachments="attachments.attachments.value"
        :station-uid="stationUid"
        :failure="attachments.failure.value"
        :hint="t('eventGroups.attachmentsHint')"
        :compact="props.compact"
        :pending="props.eventGroupId == null"
        @add="attachments.add"
        @save="attachments.save"
        @remove="attachments.remove"
        @reorder="attachments.reorder"
    />
  </FormSection>
</template>
