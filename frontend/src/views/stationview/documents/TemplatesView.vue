/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import IconButton from '@/components/button/IconButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ToggleSetting from '@/components/input/toggle/ToggleSetting.vue'
import TemplateBrowseControls from '@/components/documents/templatepicker/TemplateBrowseControls.vue'
import TemplateResults from '@/components/documents/templatepicker/TemplateResults.vue'
import TemplateTile from '@/components/documents/templatepicker/TemplateTile.vue'
import {useTemplatePages} from '@/components/documents/templatepicker/useTemplatePages'
import {DocumentTemplateKind} from '@/api/generated/schema'
import {templateTarget} from './templatesview/templateTarget'
import type {TemplateScreens} from './templateScreens'
import {useTemplateDuplication} from './useTemplateDuplication'

/**
 * The document templates of a station or an association, as tiles showing their first page: letters
 * written in Ember and uploaded PDFs filled in place. An archived template generates nothing more and
 * stays for the documents it made; the switch lists those instead of the ones in use. A station's list
 * also holds the templates of its association, marked as such, which it uses but does not change. The
 * list opens on the templates used last; search, kind and order are answered by the server over every
 * template. Any of them can be duplicated, and the editor opens on the copy. Documents are generated
 * from them in the document store and on a member's page, not here.
 */
const props = defineProps<{
  screens: TemplateScreens
}>()

const {t} = useI18n()
const router = useRouter()

const showArchived = ref(false)
const browse = useTemplatePages(query => props.screens.source.list(showArchived.value, query))

const filtered = computed(() => browse.search.value.trim() !== '' || browse.kind.value !== null)

watch(showArchived, () => browse.fromTheStart())

const duplication = useTemplateDuplication(props.screens)

function create(kind?: DocumentTemplateKind) {
  router.push({name: props.screens.editRoute, params: {id: 'new'}, query: kind ? {kind} : {}})
}
</script>

<template>
  <ViewContent :title="t(`pages.${screens.listRoute}.title`)" :subtitle="t(`pages.${screens.listRoute}.subtitle`)">
    <div class="space-y-4">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <ButtonRow>
          <PrimaryButton :icon="['fas', 'plus']" data-testid="template-new" @click="create()">
            {{ t('documentTemplates.create') }}
          </PrimaryButton>
          <SecondaryButton :icon="['fas', 'file-pdf']" data-testid="template-new-pdf" @click="create(DocumentTemplateKind.PDF)">
            {{ t('documentTemplates.createPdf') }}
          </SecondaryButton>
        </ButtonRow>
        <ToggleSetting v-model="showArchived" :label="t('documentTemplates.showArchived')"/>
      </div>
      <TemplateBrowseControls v-model:search="browse.search.value" v-model:kind="browse.kind.value"
                              v-model:sort="browse.sort.value"/>
      <FailureAlert :failure="browse.loader.failure.value ?? duplication.failure.value"/>
      <TemplateResults v-model:page="browse.page.value" :result="browse.result.value"
                       :loading="browse.loader.loading.value" :page-count="browse.pageCount.value"
                       :empty-text="filtered ? t('documentTemplates.browse.none') : t('documentTemplates.empty')"
                       data-testid="document-templates">
        <template #tile="{template}">
          <TemplateTile :template="template" :picture-url="screens.source.pictureUrl(template.id)"
                        :to="templateTarget(template, screens)">
            <template #actions>
              <IconButton :icon="['fas', 'copy']" :label="t('documentTemplates.duplicate')"
                          :disabled="duplication.running.value" data-testid="template-duplicate"
                          @click="duplication.run(template)"/>
            </template>
          </TemplateTile>
        </template>
      </TemplateResults>
    </div>
  </ViewContent>
</template>
