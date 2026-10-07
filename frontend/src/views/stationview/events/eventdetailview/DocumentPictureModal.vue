/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import Modal from '@/components/feedback/Modal.vue'
import FileThumbnail from '@/components/documents/FileThumbnail.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import {toBringPictureUrl} from '@/api/appointmentDocuments'
import type {RequiredTemplate} from '@/api/generated/schema'

/** The first page of a document an appointment asks for at full width, its placeholders shown by name. */
const open = defineModel<boolean>({required: true})

defineProps<{
  eventId: number
  template: RequiredTemplate
}>()

const LARGE_PICTURE_SIZE = 1024
</script>

<template>
  <Modal v-model="open" size="lg">
    <div class="space-y-3" data-testid="document-to-bring-picture">
      <SubHeader>{{ template.name }}</SubHeader>
      <FileThumbnail :url="toBringPictureUrl(eventId, template.templateId, LARGE_PICTURE_SIZE)" mime-type="application/pdf"
                     :alt="template.name" size="aspect-[210/297] w-full" anchor-top/>
    </div>
  </Modal>
</template>
