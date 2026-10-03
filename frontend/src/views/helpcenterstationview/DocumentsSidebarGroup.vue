/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SidebarGroup from '@/components/navigation/SidebarGroup.vue'
import SidebarLink from '@/components/navigation/SidebarLink.vue'
import SidebarExpandableLink from '@/components/navigation/SidebarExpandableLink.vue'

/**
 * The articles of the documents menu, in the order the menu shows its pages. The overview also
 * explains the reader's own documents, the page the menu opens on.
 */
defineProps<{
  close: () => void
}>()

const {t} = useI18n()
</script>

<template>
  <SidebarGroup :icon="['fas', 'file']" :label="t('sidebar.documents')"
                to="/helpcenter/station/documents" name="help-documents-module-overview" @navigate="close">
    <SidebarLink :icon="['fas', 'box-archive']" name="help-documents-store"
                 to="/helpcenter/station/documents/store" @navigate="close">
      {{ t('sidebar.documentStore') }}
    </SidebarLink>
    <SidebarExpandableLink :icon="['fas', 'file-signature']" name="help-documents-templates"
                           to="/helpcenter/station/documents/templates" @navigate="close">
      <template #label>{{ t('sidebar.documentTemplates') }}</template>
      <SidebarLink :icon="['fas', 'pen']" name="help-documents-template-edit"
                   to="/helpcenter/station/documents/templates/0" @navigate="close">
        {{ t('sidebar.edit') }}
      </SidebarLink>
    </SidebarExpandableLink>
    <SidebarLink :icon="['fas', 'clock-rotate-left']" name="help-documents-generated"
                 to="/helpcenter/station/documents/generated" @navigate="close">
      {{ t('sidebar.generatedDocuments') }}
    </SidebarLink>
    <SidebarLink :icon="['fas', 'font']" name="help-documents-fonts"
                 to="/helpcenter/station/documents/fonts" @navigate="close">
      {{ t('sidebar.documentFonts') }}
    </SidebarLink>
  </SidebarGroup>
</template>
