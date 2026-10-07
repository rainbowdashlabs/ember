/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SidebarGroup from '@/components/navigation/SidebarGroup.vue'
import SidebarLink from '@/components/navigation/SidebarLink.vue'
import {StationPermission} from '@/api/generated/schema'
import {useSession} from '@/composables/useSession'

/**
 * The documents of the station. The group itself opens the reader's own documents, which every member
 * has; the store, the templates, the list of generated documents and the fonts each need their right.
 */
defineProps<{
  openGroup: string | null
  isDesktop: boolean
}>()

const emit = defineEmits<{
  (e: 'update:openGroup', value: string | null): void
  (e: 'navigate'): void
}>()

const {t} = useI18n()
const {hasPermission} = useSession()

function close() {
  emit('navigate')
}
</script>

<template>
  <SidebarGroup :open-group="isDesktop ? undefined : openGroup" :icon="['fas', 'file']" :label="t('sidebar.documents')"
                to="/station/documents" name="documents-own" @update:open-group="v => emit('update:openGroup', v)" @navigate="close">
    <SidebarLink v-if="hasPermission(StationPermission.DOCUMENT_READ)" :icon="['fas', 'box-archive']" name="documents-store"
                 to="/station/documents/store" @navigate="close">
      {{ t('sidebar.documentStore') }}
    </SidebarLink>
    <SidebarLink v-if="hasPermission(StationPermission.DOCUMENT_TEMPLATE_EDIT)" :icon="['fas', 'file-signature']" name="documents-templates"
                 to="/station/documents/templates" @navigate="close">
      {{ t('sidebar.documentTemplates') }}
    </SidebarLink>
    <SidebarLink v-if="hasPermission(StationPermission.DOCUMENT_READ_MEMBER)" :icon="['fas', 'clock-rotate-left']" name="documents-generated"
                 to="/station/documents/generated" @navigate="close">
      {{ t('sidebar.generatedDocuments') }}
    </SidebarLink>
    <SidebarLink v-if="hasPermission(StationPermission.DOCUMENT_TEMPLATE_EDIT)" :icon="['fas', 'font']" name="documents-fonts"
                 to="/station/documents/fonts" @navigate="close">
      {{ t('sidebar.documentFonts') }}
    </SidebarLink>
  </SidebarGroup>
</template>
