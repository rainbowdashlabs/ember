/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import FederationCard from '../eventeditview/FederationCard.vue'
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
 * arranges no partner places, so the share card is given none and shows none.
 */
defineProps<{
  federationShare: ReturnType<typeof useEventFederationShare>
  fieldDefaults: ReturnType<typeof useEventFieldDefaults>
  /** The fields of the template's sheet that none of its own questions already answers. */
  prefillableFields: AttendanceTemplateField[]
  canFederate: boolean
  attachments: ReturnType<typeof useEventAttachments>
  stationUid: string
}>()

const {t} = useI18n()
</script>

<template>
  <FederationCard
      :shared="federationShare.state.federationShared"
      :scope="federationShare.state.federationScope"
      :partner-ids="federationShare.state.federationPartnerIds"
      :partners="federationShare.partners.value"
      :can-federate="canFederate"
      :hint="t('eventTemplates.federationHint')"
      @update:shared="federationShare.handlers['update:federationShared']"
      @update:scope="federationShare.handlers['update:federationScope']"
      @update:partner-ids="federationShare.handlers['update:federationPartnerIds']"
  />
  <FieldDefaultsCard
      v-if="prefillableFields.length > 0"
      :fields="prefillableFields"
      :defaults="fieldDefaults.defaults.value"
      @update:source="fieldDefaults.setSource"
      @update:value="fieldDefaults.setValue"
  />
  <AttachmentsCard
      :attachments="attachments.attachments.value"
      :station-uid="stationUid"
      :failure="attachments.failure.value"
      :hint="t('eventTemplates.attachmentsHint')"
      @add="attachments.add"
      @save="attachments.save"
      @remove="attachments.remove"
      @reorder="attachments.reorder"
  />
</template>
