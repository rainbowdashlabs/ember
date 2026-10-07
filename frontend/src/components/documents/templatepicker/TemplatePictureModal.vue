/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import Modal from '@/components/feedback/Modal.vue'
import FileThumbnail from '@/components/documents/FileThumbnail.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import type {DocumentTemplateSummary} from '@/api/generated/schema'
import TemplateBadges from './TemplateBadges.vue'

/**
 * The first page of a template at full width, for telling apart templates that look alike on their
 * tiles before choosing one.
 */
const open = defineModel<boolean>({required: true})

defineProps<{
  template: DocumentTemplateSummary
  pictureUrl: string
}>()
</script>

<template>
  <Modal v-model="open" size="lg">
    <div class="space-y-3" data-testid="template-picture-modal">
      <SubHeader>{{ template.name }}</SubHeader>
      <TemplateBadges :template="template"/>
      <FileThumbnail :url="pictureUrl" mime-type="application/pdf" :alt="template.name" size="aspect-[210/297] w-full"
                     anchor-top/>
    </div>
  </Modal>
</template>
