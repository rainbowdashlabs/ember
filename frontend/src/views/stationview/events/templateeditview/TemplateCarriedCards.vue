/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import FormSection from '@/components/container/FormSection.vue'
import FederationCard from '../eventeditview/FederationCard.vue'
import {documentsSummary, sharingSummary} from '../eventshared/sectionSummaries'
import FieldDefaultsCard from '../eventeditview/FieldDefaultsCard.vue'
import AttachmentsCard from '../eventeditview/AttachmentsCard.vue'
import type {useEventAttachments} from '../eventeditview/useEventAttachments'
import type {useEventFederationShare} from '../eventeditview/useEventFederationShare'
import type {useEventFieldDefaults} from '../eventeditview/useEventFieldDefaults'
import type {AttendanceTemplateField} from '@/api/generated/schema'

/**
 * The partner share, the attendance field starting values and the files a template hands its
 * appointments, drawn with the appointment editor's own cards.
 *
 * <p>The view holds all three; this only shows them. The share and the starting values are saved
 * with the template, the files are written as they change, as on an appointment. A template
 * arranges no partner places, so the share card is given none and shows none. The share is a folded
 * section saying with whom the appointments are shared, as in the appointment form. The files share a
 * folded section with the documents to bring, which the view places in the slot.
 */
defineProps<{
  federationShare: ReturnType<typeof useEventFederationShare>
  fieldDefaults: ReturnType<typeof useEventFieldDefaults>
  /** The fields of the template's sheet that none of its own questions already answers. */
  prefillableFields: AttendanceTemplateField[]
  canFederate: boolean
  attachments: ReturnType<typeof useEventAttachments>
  stationUid: string
  /** How many documents to bring the template asks for, which the view edits in the slot beside the files. */
  documentCount: number
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer sheet>
    <FormSection
        :title="t('formSections.sharing')"
        :summary="sharingSummary(federationShare.state.federationShared, federationShare.state.federationScope,
                                 federationShare.state.federationPartnerIds, t)"
        data-testid="form-section-sharing"
    >
      <FederationCard
          :shared="federationShare.state.federationShared"
          :scope="federationShare.state.federationScope"
          :partner-ids="federationShare.state.federationPartnerIds"
          :partners="federationShare.partners.value"
          :can-federate="canFederate"
          :hint="t('eventTemplates.federationHint')"
          compact
          @update:shared="federationShare.handlers['update:federationShared']"
          @update:scope="federationShare.handlers['update:federationScope']"
          @update:partner-ids="federationShare.handlers['update:federationPartnerIds']"
      />
    </FormSection>
  </NeutralContainer>
  <FieldDefaultsCard
      v-if="prefillableFields.length > 0"
      :fields="prefillableFields"
      :defaults="fieldDefaults.defaults.value"
      @update:source="fieldDefaults.setSource"
      @update:value="fieldDefaults.setValue"
  />
  <NeutralContainer sheet>
    <FormSection
        :title="t('formSections.documents')"
        :summary="documentsSummary(documentCount, attachments.attachments.value.length, t)"
        data-testid="form-section-documents"
    >
      <slot/>
      <AttachmentsCard
          :attachments="attachments.attachments.value"
          :station-uid="stationUid"
          :failure="attachments.failure.value"
          :hint="t('eventTemplates.attachmentsHint')"
          compact
          @add="attachments.add"
          @save="attachments.save"
          @remove="attachments.remove"
          @reorder="attachments.reorder"
      />
    </FormSection>
  </NeutralContainer>
</template>
