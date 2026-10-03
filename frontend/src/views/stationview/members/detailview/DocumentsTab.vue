/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import MemberDocumentsPanel from '@/components/documents/MemberDocumentsPanel.vue'
import {StationModule, StationPermission, type MemberWithName} from '@/api/generated/schema'
import {useSession} from '@/composables/useSession'
import GenerateDocumentModal from './GenerateDocumentModal.vue'

/**
 * The documents of a member, and the button that generates one from a template and files it with
 * them. Generating needs the same right as filing a document for somebody.
 */
const props = defineProps<{
  memberId: number
  canEdit: boolean
  allMembers: MemberWithName[]
}>()

const {t} = useI18n()
const {hasPermission, isModuleEnabled} = useSession()

const canGenerate = computed(() =>
    isModuleEnabled(StationModule.DOCUMENTS) && hasPermission(StationPermission.DOCUMENT_EDIT_MEMBER))
const generating = ref(false)
const filed = ref(0)
</script>

<template>
  <div class="space-y-4">
    <div v-if="canGenerate" class="flex justify-end">
      <PrimaryButton :icon="['fas', 'file-circle-plus']" data-testid="document-generate-open" @click="generating = true">
        {{ t('documentTemplates.generateOpen') }}
      </PrimaryButton>
    </div>
    <MemberDocumentsPanel
        :key="filed"
        :member-id="props.memberId"
        :can-upload="canEdit"
        :can-edit="canEdit"
        :all-members="allMembers"
    />
    <GenerateDocumentModal v-if="generating" v-model="generating" :member-id="props.memberId" @filed="filed++"/>
  </div>
</template>
