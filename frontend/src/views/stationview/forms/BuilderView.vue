/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import DateTimeInput from '@/components/input/datetime/DateTimeInput.vue'
import RestrictionPicker from '@/components/input/RestrictionPicker.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import QuestionEditor from './builderview/QuestionEditor.vue'
import type { QuestionDraft } from './builderview/types'
import type { FormQuestionRequest, QuestionType, MemberGroup, StationMember, UserTag } from '@/api/types'
import { QuestionTypes } from '@/api/types'
import { forms, memberGroups, userTags, stationMembers } from '@/api'
import FieldLabel from '@/components/typography/FieldLabel.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const formId = computed(() => route.params.id ? Number(route.params.id) : null)

const loading = ref(false)
const error = ref('')

// Form metadata
const title = ref('')
const description = ref('')
const shuffleQuestions = ref(false)
const allowEdit = ref(true)
const forced = ref(false)
const startAt = ref('')
const endAt = ref('')

// Restrictions
const allGroups = ref<MemberGroup[]>([])
const allTags = ref<UserTag[]>([])
const allMembers = ref<StationMember[]>([])
const selectedUserTypes = ref<string[]>([])
const selectedGroupIds = ref<number[]>([])
const selectedTagIds = ref<number[]>([])
const selectedMemberIds = ref<number[]>([])

// Questions
const questions = ref<QuestionDraft[]>([])
let nextTempId = 1

const questionTypes: QuestionType[] = [QuestionTypes.CHOICE, QuestionTypes.TEXT, QuestionTypes.RATING, QuestionTypes.DATE, QuestionTypes.RANKING, QuestionTypes.LIKERT]

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

async function loadForm() {
  loading.value = true
  try {
    const [groups, tags, members] = await Promise.all([
      memberGroups.listGroups(),
      userTags.listTags(),
      stationMembers.listMembers(),
    ])
    allGroups.value = groups
    allTags.value = tags
    allMembers.value = members

    if (!formId.value) { loading.value = false; return }

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
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

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
      config: JSON.stringify(q.config),
    }))
    await forms.setQuestions(id!, questionRequests)

    await forms.setRestrictions(id!, {
      userTypes: selectedUserTypes.value,
      groupIds: selectedGroupIds.value,
      tagIds: selectedTagIds.value,
      memberIds: selectedMemberIds.value,
    })

    router.push({ name: 'forms-list' })
  } catch {
    error.value = t('common.error')
  }
}

onMounted(loadForm)
</script>

<template>
  <ViewContent>
    <div class="space-y-6 max-w-3xl">
      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading">
        <!-- Form Metadata -->
        <NeutralContainer>
          <div class="space-y-4">
            <TextInput v-model="title" :placeholder="t('forms.title')" />
            <TextAreaInput v-model="description" :placeholder="t('forms.description')" />
            <div class="grid grid-cols-2 gap-4">
              <div>
                <FieldLabel hint class="mb-1">{{ t('forms.startAt') }}</FieldLabel>
                <DateTimeInput v-model="startAt" />
              </div>
              <div>
                <FieldLabel hint class="mb-1">{{ t('forms.endAt') }}</FieldLabel>
                <DateTimeInput v-model="endAt" />
              </div>
            </div>
            <div class="flex gap-6 flex-wrap">
              <FieldLabel inline>
                <ToggleInput v-model="shuffleQuestions" />
                {{ t('forms.shuffleQuestions') }}
              </FieldLabel>
              <FieldLabel inline>
                <ToggleInput v-model="allowEdit" />
                {{ t('forms.allowEdit') }}
              </FieldLabel>
              <FieldLabel inline>
                <ToggleInput v-model="forced" />
                {{ t('forms.forced') }}
              </FieldLabel>
            </div>
          </div>
        </NeutralContainer>

        <!-- Restrictions -->
        <NeutralContainer>
          <div class="space-y-4">
            <SubHeader>{{ t('forms.restrictions.title') }}</SubHeader>
            <RestrictionPicker
              :groups="allGroups"
              :tags="allTags"
              :members="allMembers"
              :selected-user-types="selectedUserTypes"
              :selected-group-ids="selectedGroupIds"
              :selected-tag-ids="selectedTagIds"
              :selected-member-ids="selectedMemberIds"
              show-members
              @update:selected-user-types="selectedUserTypes = $event"
              @update:selected-group-ids="selectedGroupIds = $event"
              @update:selected-tag-ids="selectedTagIds = $event"
              @update:selected-member-ids="selectedMemberIds = $event"
            />
          </div>
        </NeutralContainer>

        <!-- Questions -->
        <div class="space-y-3">
          <QuestionEditor v-for="(q, idx) in questions" :key="q.id"
              :question="q" :index="idx" :total-questions="questions.length"
              @move="moveQuestion" @remove="removeQuestion" />
        </div>

        <!-- Add Question -->
        <div class="flex flex-wrap gap-2">
          <SecondaryButton :icon="['fas', 'plus']" v-for="type in questionTypes" :key="type" @click="addQuestion(type)">
            {{ t(`forms.questionTypes.${type}`) }}
          </SecondaryButton>
        </div>

        <!-- Actions -->
        <div class="flex justify-end gap-3">
          <SecondaryButton @click="router.push({ name: 'forms-list' })">{{ t('common.cancel') }}</SecondaryButton>
          <PrimaryButton @click="save">{{ t('common.save') }}</PrimaryButton>
        </div>
      </template>
    </div>
  </ViewContent>
</template>
