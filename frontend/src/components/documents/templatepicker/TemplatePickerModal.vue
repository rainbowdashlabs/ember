/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import IconButton from '@/components/button/IconButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import {stationTemplateSource, type TemplateListQuery, type TemplatePages} from '@/api/documentTemplates'
import type {DocumentTemplateSummary} from '@/api/generated/schema'
import TemplateBrowseControls from './TemplateBrowseControls.vue'
import TemplatePictureModal from './TemplatePictureModal.vue'
import TemplateResults from './TemplateResults.vue'
import TemplateTile from './TemplateTile.vue'
import {useTemplatePages} from './useTemplatePages'

/**
 * Choosing templates by what they look like: a search, a kind, an order and a page of tiles, all
 * answered by the server over every template. Any tile can be opened large first.
 *
 * <p>With `multiple` a press marks a tile and a button takes every marked one; without, a press takes
 * the template and closes. Templates already chosen where the dialog was opened are marked and cannot
 * be taken twice.
 */
const open = defineModel<boolean>({required: true})

const props = withDefaults(defineProps<{
  /** Reads a page of the templates on offer. */
  pages: TemplatePages
  /** What every request carries besides the reader's choice. */
  fixed?: TemplateListQuery
  /** Where the picture of a template is served. */
  pictureUrl?: (id: number) => string
  multiple?: boolean
  /** The templates already chosen. */
  chosenIds?: readonly number[]
}>(), {
  fixed: () => ({}),
  pictureUrl: (id: number) => stationTemplateSource.pictureUrl(id),
  multiple: false,
  chosenIds: () => [],
})

const emit = defineEmits<{
  pick: [templates: DocumentTemplateSummary[]]
}>()

const {t} = useI18n()

const browse = useTemplatePages(props.pages, {fixed: () => props.fixed, autoLoad: false})
const marked = ref<DocumentTemplateSummary[]>([])
const enlarged = ref<DocumentTemplateSummary | null>(null)
const showEnlarged = computed({
  get: () => enlarged.value !== null,
  set: shown => { if (!shown) enlarged.value = null },
})

watch(open, opened => {
  if (!opened) return
  marked.value = []
  browse.fromTheStart()
}, {immediate: true})

function isMarked(template: DocumentTemplateSummary) {
  return marked.value.some(other => other.id === template.id)
}

function choose(template: DocumentTemplateSummary) {
  if (!props.multiple) {
    take([template])
    return
  }
  marked.value = isMarked(template)
    ? marked.value.filter(other => other.id !== template.id)
    : [...marked.value, template]
}

function take(templates: DocumentTemplateSummary[]) {
  emit('pick', templates)
  open.value = false
}
</script>

<template>
  <Modal v-model="open" size="2xl" mobile-full>
    <div class="space-y-4" data-testid="template-picker">
      <SubHeader>{{ multiple ? t('documentTemplates.browse.chooseMany') : t('documentTemplates.chooseTemplate') }}</SubHeader>
      <TemplateBrowseControls v-model:search="browse.search.value" v-model:kind="browse.kind.value"
                              v-model:sort="browse.sort.value"/>
      <FailureAlert :failure="browse.loader.failure.value"/>
      <TemplateResults v-model:page="browse.page.value" :result="browse.result.value"
                       :loading="browse.loader.loading.value" :page-count="browse.pageCount.value"
                       :empty-text="t('documentTemplates.browse.none')">
        <template #tile="{template}">
          <TemplateTile :template="template" :picture-url="pictureUrl(template.id)"
                        :chosen="chosenIds.includes(template.id) || isMarked(template)"
                        :disabled="chosenIds.includes(template.id)" @choose="choose">
            <template #actions>
              <IconButton :icon="['fas', 'magnifying-glass-plus']" :label="t('documentTemplates.browse.enlarge')"
                          data-testid="template-enlarge" @click="enlarged = template"/>
            </template>
          </TemplateTile>
        </template>
      </TemplateResults>
      <ButtonRow v-if="multiple" pair align="end">
        <SecondaryButton @click="open = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="marked.length === 0" data-testid="template-picker-take" @click="take(marked)">
          {{ t('documentTemplates.browse.takeMany', {count: marked.length}) }}
        </PrimaryButton>
      </ButtonRow>
    </div>
    <TemplatePictureModal v-if="enlarged" v-model="showEnlarged" :template="enlarged"
                          :picture-url="pictureUrl(enlarged.id)"/>
  </Modal>
</template>
