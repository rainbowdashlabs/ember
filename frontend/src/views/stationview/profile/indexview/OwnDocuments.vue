/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import MemberDocumentsPanel from '@/components/documents/MemberDocumentsPanel.vue'
import {StationModule} from '@/api/generated/schema'
import {useSession} from '@/composables/useSession'
import SelfServiceDocuments from './SelfServiceDocuments.vue'

/**
 * The documents of one member on the own profile page, the reader's own or those of a child, with
 * the documents that can be generated for them next to it. A generated one appears in the list at once.
 */
defineProps<{
  memberId: number
  canUpload: boolean
  title?: string
}>()

const {isModuleEnabled} = useSession()
const filed = ref(0)
</script>

<template>
  <div class="space-y-3">
    <MemberDocumentsPanel :key="filed" :member-id="memberId" :can-upload="canUpload" :title="title"/>
    <SelfServiceDocuments v-if="isModuleEnabled(StationModule.DOCUMENTS)" :member-id="memberId" @filed="filed++"/>
  </div>
</template>
