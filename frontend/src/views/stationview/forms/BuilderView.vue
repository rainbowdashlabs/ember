/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import QuestionEditor from './builderview/QuestionEditor.vue'
import FormMetadataEditor from './builderview/FormMetadataEditor.vue'
import FormRestrictionsEditor from './builderview/FormRestrictionsEditor.vue'
import type { QuestionDraft } from './builderview/types'
import type { FormPurposeName, FormQuestionRequest, QuestionType, MemberGroup, StationMember, UserTag } from '@/api/types'
import { FormPurpose, QuestionTypes, QUESTION_TYPES_BY_PURPOSE } from '@/api/types'
import { forms, memberGroups, userTags, stationMembers } from '@/api'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const formId = computed(() => route.params.id ? Number(route.params.id) : null)

/**
 * The form's purpose. Loaded from the form when editing, derived from the
 * `?purpose=…` query when creating, defaults to INTERNAL.
 */
const purpose = ref<FormPurposeName>(FormPurpose.INTERNAL)

/** Route name to return to after save/cancel, derived from the current form's purpose. */
const returnRouteName = computed(() => {
  if (purpose.value === FormPurpose.CONTACT) return 'pages-forms'
  if (purpose.value === FormPurpose.POLL) return 'pages-polls'
  return 'forms-list'
})

const title = ref('')
const description = ref('')
const shuffleQuestions = ref(false)
const allowEdit = ref(true)
const forced = ref(false)
const startAt = ref('')
const endAt = ref('')

const allGroups = ref<MemberGroup[]>([])
const allTags = ref<UserTag[]>([])
const allMembers = ref<StationMember[]>([])
const selectedUserTypes = ref<string[]>([])
const selectedGroupIds = ref<number[]>([])
const selectedTagIds = ref<number[]>([])
const selectedMemberIds = ref<number[]>([])

const questions = ref<QuestionDraft[]>([])
let nextTempId = 1

const questionTypes = computed<QuestionType[]>(() => QUESTION_TYPES_BY_PURPOSE[purpose.value])

function addQuestion(type: QuestionType) {
  const defaultConfig = getDefaultConfig(type)
  questions.value.push({
    id: `temp-${nextTempId++}`,
    questionType: type,
    title: '',
    description: '',
    required: false,
    shuffle: false,
    config: defaultConfig,
  })
}

function getDefaultConfig(type: QuestionType): Record<string, unknown> {
  switch (type) {
    case QuestionTypes.CHOICE: return { multiSelect: false, dropdown: false, allowOther: false, options: [''], multiLimitType: 'NONE', multiLimit: null }
    case QuestionTypes.TEXT: return { longAnswer: false }
    case QuestionTypes.RATING: return { scale: 5, icon: 'STAR' }
    case QuestionTypes.DATE: return {}
    case QuestionTypes.RANKING: return { options: [''] }
    case QuestionTypes.LIKERT: return { statements: [''], scaleMin: 1, scaleMax: 5, scaleLabels: [] }
  }
}

function removeQuestion(index: number) {
  questions.value.splice(index, 1)
}

function moveQuestion(index: number, direction: -1 | 1) {
  const newIndex = index + direction
  if (newIndex < 0 || newIndex >= questions.value.length) return
  const temp = questions.value[index]
  questions.value[index] = questions.value[newIndex]
  questions.value[newIndex] = temp
}

const { loading, error } = useAsyncLoader(async () => {
  const [groups, tags, members] = await Promise.all([
    memberGroups.listGroups(),
    userTags.listTags(),
    stationMembers.listMembers(),
  ])
  allGroups.value = groups
  allTags.value = tags
  allMembers.value = members

  if (!formId.value) {
    const queryPurpose = typeof route.query.purpose === 'string' ? route.query.purpose : null
    if (queryPurpose && queryPurpose in FormPurpose) {
      purpose.value = queryPurpose as FormPurposeName
    }
    return
  }

  const [form, qs, restrictions] = await Promise.all([
    forms.getForm(formId.value),
    forms.getQuestions(formId.value),
    forms.getRestrictions(formId.value),
  ])
  title.value = form.title
  description.value = form.description
  shuffleQuestions.value = form.shuffleQuestions
  allowEdit.value = form.allowEdit
  forced.value = form.forced ?? false
  startAt.value = form.startAt ? form.startAt.slice(0, 16) : ''
  endAt.value = form.endAt ? form.endAt.slice(0, 16) : ''
  purpose.value = form.purpose

  selectedUserTypes.value = restrictions.userTypes ?? []
  selectedGroupIds.value = restrictions.groupIds ?? []
  selectedTagIds.value = restrictions.tagIds ?? []
  selectedMemberIds.value = restrictions.memberIds ?? []

  questions.value = qs.map(q => ({
    id: `existing-${q.id}`,
    questionType: q.formQuestionType,
    title: q.title,
    description: q.description,
    required: q.required,
    shuffle: q.shuffle,
    config: typeof q.config === 'object' ? { ...q.config } : {},
  }))
})

async function save() {
  error.value = ''
  try {
    let id = formId.value
    const formData = {
      title: title.value,
      description: description.value,
      shuffleQuestions: shuffleQuestions.value,
      allowEdit: allowEdit.value,
      forced: forced.value,
      startAt: startAt.value ? new Date(startAt.value).toISOString() : null,
      endAt: endAt.value ? new Date(endAt.value).toISOString() : null,
      purpose: purpose.value,
    }

    if (id) {
      await forms.updateForm(id, formData)
    } else {
      const created = await forms.createForm(formData)
      id = created.id
    }

    const questionRequests: FormQuestionRequest[] = questions.value.map(q => ({
      questionType: q.questionType,
      title: q.title,
      description: q.description,
      required: q.required,
      shuffle: q.shuffle,
      config: {...(q.config as object), questionType: q.questionType},
    }))
    await forms.setQuestions(id!, questionRequests)

    await forms.setRestrictions(id!, {
      userTypes: selectedUserTypes.value,
      groupIds: selectedGroupIds.value,
      tagIds: selectedTagIds.value,
      memberIds: selectedMemberIds.value,
    })

    router.push({ name: returnRouteName.value })
  } catch (e) {
    error.value = t('common.error')
    throw e
  }
}
</script>

<template>
  <ViewContent>
    <div class="space-y-6 max-w-3xl">
      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading">
        <FormMetadataEditor
          v-model:title="title"
          v-model:description="description"
          v-model:start-at="startAt"
          v-model:end-at="endAt"
          v-model:shuffle-questions="shuffleQuestions"
          v-model:allow-edit="allowEdit"
          v-model:forced="forced"
        />

        <FormRestrictionsEditor
          :groups="allGroups"
          :tags="allTags"
          :members="allMembers"
          v-model:selected-user-types="selectedUserTypes"
          v-model:selected-group-ids="selectedGroupIds"
          v-model:selected-tag-ids="selectedTagIds"
          v-model:selected-member-ids="selectedMemberIds"
        />

        <div class="space-y-3">
          <QuestionEditor v-for="(q, idx) in questions" :key="q.id"
              :question="q" :index="idx" :total-questions="questions.length"
              @move="moveQuestion" @remove="removeQuestion" />
        </div>

        <div class="flex flex-wrap gap-2">
          <SecondaryButton :icon="['fas', 'plus']" v-for="type in questionTypes" :key="type" @click="addQuestion(type)">
            {{ t(`forms.questionTypes.${type}`) }}
          </SecondaryButton>
        </div>

        <div class="flex justify-end gap-3">
          <SecondaryButton @click="router.push({ name: returnRouteName })">{{ t('common.cancel') }}</SecondaryButton>
          <SaveButton :action="save"/>
        </div>
      </template>
    </div>
  </ViewContent>
</template>
