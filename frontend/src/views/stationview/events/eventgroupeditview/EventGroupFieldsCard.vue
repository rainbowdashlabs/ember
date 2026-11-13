/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import EventFieldList from '../eventshared/EventFieldList.vue'
import {OfferedFieldTypes} from '@/api/fieldTypes'
import type {EventFieldEntry} from '@/api/generated/schema'

/**
 * The fields an event group hands down to every appointment of it, such as its place. Only plain
 * values: what names members or is answered per date stays on the appointment.
 */
const fields = defineModel<EventFieldEntry[]>({required: true})

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3" data-testid="event-group-fields">
    <SectionHeader>{{ t('eventGroups.fields') }}</SectionHeader>
    <MutedText tag="p" size="sm">{{ t('eventGroups.fieldsHint') }}</MutedText>
    <EventFieldList v-model:fields="fields" show-value :offered-types="OfferedFieldTypes.EVENT_GROUP"/>
  </NeutralContainer>
</template>
