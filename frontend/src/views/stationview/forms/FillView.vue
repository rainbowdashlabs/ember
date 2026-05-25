/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import InfoContainer from '@/components/container/InfoContainer.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import IconButton from '@/components/button/IconButton.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import type { Form, FormQuestion } from '@/api/types'
import { QuestionTypes } from '@/api/types'
import { forms } from '@/api'
import type { EligibleMembers } from '@/api/forms'
import { useSession } from '@/composables/useSession'
import SectionHeader from '@/components/typography/SectionHeader.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const { sessionInfo, loaded } = useSession()

const formId = computed(() => Number(route.params.id))
const loading = ref(true)
const error = ref('')
const form = ref<Form | null>(null)
const questions = ref<FormQuestion[]>([])
const answers = ref<Record<number, unknown>>({})
const hasExistingResponse = ref(false)

// Member selection — driven by backend eligibility
const selectedMemberId = ref<number | null>(null)
const eligibility = ref<EligibleMembers | null>(null)
const managedMembers = computed(() => sessionInfo.value?.managedMembers ?? [])

const canFillForSelf = computed(() => eligibility.value?.selfEligible ?? false)

const eligibleManagedMembers = computed(() => {
  if (!eligibility.value) return []
  const eligibleIds = new Set(eligibility.value.eligibleManagedMemberIds)
  return managedMembers.value.filter(m => eligibleIds.has(m.id))
})

const showMemberSelector = computed(() => {
  // Show selector when there are multiple targets (self + managed, or multiple managed)
  const targets = (canFillForSelf.value ? 1 : 0) + eligibleManagedMembers.value.length
  return targets > 1
})

const showSingleManagedHint = computed(() => {
  // Show hint when can't fill for self but exactly one managed member is eligible
  return !canFillForSelf.value && eligibleManagedMembers.value.length === 1
})

const fillTargetOptions = computed(() => {
  const options: { id: number | null; label: string }[] = []
  if (canFillForSelf.value) {
    const name = sessionInfo.value?.account
        ? [sessionInfo.value.account.firstName, sessionInfo.value.account.lastName].filter(Boolean).join(' ')
        : ''
    options.push({ id: null, label: t('forms.fillForSelf', { name: name || t('forms.fillForSelfDefault') }) })
  }
  for (const m of eligibleManagedMembers.value) {
    options.push({ id: m.id, label: m.name || m.email || `#${m.id}` })
  }
  return options
})

const effectiveMemberId = computed(() => selectedMemberId.value)

function parseConfig(config: string): Record<string, unknown> {
  try { return JSON.parse(config || '{}') } catch { return {} }
}

function initAnswerDefaults() {
  for (const q of questions.value) {
    if (q.questionType === QuestionTypes.CHOICE) answers.value[q.id] = { selected: [], other: '' }
    else if (q.questionType === QuestionTypes.TEXT) answers.value[q.id] = { text: '' }
    else if (q.questionType === QuestionTypes.RATING) answers.value[q.id] = { rating: 0 }
    else if (q.questionType === QuestionTypes.DATE) answers.value[q.id] = { date: '' }
    else if (q.questionType === QuestionTypes.RANKING) {
      const cfg = parseConfig(q.config)
      const opts = (cfg.options as string[]) || []
      answers.value[q.id] = { order: opts.map((_: string, i: number) => i) }
    }
    else if (q.questionType === QuestionTypes.LIKERT) answers.value[q.id] = { ratings: {} }
  }
}

async function loadExistingResponse() {
  hasExistingResponse.value = false
  answers.value = {}

  try {
    let response
    if (effectiveMemberId.value) {
      // Loading for a managed member — use the response detail endpoint via analytics
      // We don't have a direct "get response for member X" on the fill side,
      // so we try submitting with the existing getMyResponse (which is for self)
      // Actually we need to check if the managed member already responded.
      // The simplest approach: try to get responses list and find the managed member's
      // For now, just initialize defaults for managed members
      initAnswerDefaults()
      return
    }
    response = await forms.getMyResponse(formId.value)
    if (response.response) {
      hasExistingResponse.value = true
      for (const answer of response.answers) {
        try {
          answers.value[answer.questionId] = JSON.parse(answer.value)
        } catch {
          answers.value[answer.questionId] = {}
        }
      }
    } else {
      initAnswerDefaults()
    }
  } catch {
    initAnswerDefaults()
  }
}

async function loadData() {
  loading.value = true
  try {
    const [f, qs, elig] = await Promise.all([
      forms.getForm(formId.value),
      forms.getQuestions(formId.value),
      forms.getEligibleMembers(formId.value),
    ])
    eligibility.value = elig
    form.value = f
    questions.value = qs

    // Default selection: prefer self if eligible, otherwise first eligible managed member
    if (canFillForSelf.value) {
      selectedMemberId.value = null // self
    } else if (eligibleManagedMembers.value.length > 0) {
      selectedMemberId.value = eligibleManagedMembers.value[0].id
    }

    await loadExistingResponse()
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

// When member selection changes, reload existing response
watch(selectedMemberId, async () => {
  if (!loading.value) {
    await loadExistingResponse()
  }
})

function toggleChoice(questionId: number, optionIndex: number, multi: boolean) {
  const ans = answers.value[questionId] as { selected: number[]; other: string }
  if (multi) {
    const idx = ans.selected.indexOf(optionIndex)
    if (idx >= 0) ans.selected.splice(idx, 1)
    else ans.selected.push(optionIndex)
  } else {
    ans.selected = [optionIndex]
  }
}

function setRating(questionId: number, value: number) {
  (answers.value[questionId] as { rating: number }).rating = value
}

function setLikertRating(questionId: number, stmtIndex: number, value: number) {
  const ans = answers.value[questionId] as { ratings: Record<string, number> }
  ans.ratings[String(stmtIndex)] = value
}

function moveRankItem(questionId: number, fromIdx: number, direction: -1 | 1) {
  const ans = answers.value[questionId] as { order: number[] }
  const toIdx = fromIdx + direction
  if (toIdx < 0 || toIdx >= ans.order.length) return
  const temp = ans.order[fromIdx]
  ans.order[fromIdx] = ans.order[toIdx]
  ans.order[toIdx] = temp
}

function ratingIcon(icon: string): string[] {
  if (icon === 'HEART') return ['fas', 'heart']
  if (icon === 'THUMB_UP') return ['fas', 'thumbs-up']
  return ['fas', 'star']
}

async function submit() {
  error.value = ''
  try {
    const answerMap: Record<number, string> = {}
    for (const [qId, value] of Object.entries(answers.value)) {
      answerMap[Number(qId)] = JSON.stringify(value)
    }

    if (effectiveMemberId.value) {
      // Submitting for a managed member
      if (hasExistingResponse.value) {
        await forms.updateForMember(formId.value, effectiveMemberId.value, { answers: answerMap })
      } else {
        await forms.submitForMember(formId.value, effectiveMemberId.value, { answers: answerMap })
      }
    } else {
      // Submitting for self
      if (hasExistingResponse.value) {
        await forms.updateResponse(formId.value, { answers: answerMap })
      } else {
        await forms.submitResponse(formId.value, { answers: answerMap })
      }
    }
    router.push({ name: 'forms-list' })
  } catch {
    error.value = t('common.error')
  }
}

onMounted(() => {
  if (loaded.value) loadData()
})

watch(loaded, (isLoaded) => {
  if (isLoaded && loading.value) loadData()
})
</script>

<template>
  <ViewContent>
    <div class="space-y-6 max-w-3xl">
      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading && form">
        <div>
          <SectionHeader>{{ form.title }}</SectionHeader>
          <p v-if="form.description" class="text-(--text-muted) mt-1">{{ form.description }}</p>
        </div>

        <!-- Member selector for member managers -->
        <InfoContainer v-if="showMemberSelector">
          <div class="space-y-2">
            <p class="text-sm font-medium">{{ t('forms.fillForWhom') }}</p>
            <SelectInput
                :model-value="String(selectedMemberId ?? '')"
                @update:model-value="(v: string | undefined) => selectedMemberId = v ? Number(v) : null"
            >
              <option v-for="opt in fillTargetOptions" :key="String(opt.id)" :value="String(opt.id ?? '')">
                {{ opt.label }}
              </option>
            </SelectInput>
            <p v-if="selectedMemberId" class="text-xs text-(--text-muted)">
              {{ t('forms.fillForMemberHint') }}
            </p>
          </div>
        </InfoContainer>

        <!-- Single managed member hint (no selector needed) -->
        <InfoContainer v-if="showSingleManagedHint">
          <p class="text-sm">
            {{ t('forms.fillForSingleManaged', { name: eligibleManagedMembers[0].name || eligibleManagedMembers[0].email }) }}
          </p>
        </InfoContainer>

        <div class="space-y-4">
          <NeutralContainer v-for="q in questions" :key="q.id">
            <div class="space-y-3">
              <div>
                <span class="font-medium">{{ q.title }}</span>
                <span v-if="q.required" class="text-error ml-1">*</span>
                <p v-if="q.description" class="text-xs text-(--text-muted) mt-0.5">{{ q.description }}</p>
              </div>

              <!-- CHOICE -->
              <template v-if="q.questionType === QuestionTypes.CHOICE">
                <div class="space-y-1">
                  <template v-if="parseConfig(q.config).dropdown">
                    <SelectInput
                        :model-value="String((answers[q.id] as { selected: number[] })?.selected?.[0] ?? '')"
                        @update:model-value="(v: string | undefined) => toggleChoice(q.id, Number(v), false)">
                      <option value="">--</option>
                      <option v-for="(opt, oi) in (parseConfig(q.config).options as string[])" :key="oi" :value="oi">{{ opt }}</option>
                    </SelectInput>
                  </template>
                  <template v-else>
                    <div v-for="(opt, oi) in (parseConfig(q.config).options as string[])" :key="oi" class="py-1">
                      <SelectionToggleButton
                          :selected="(answers[q.id] as { selected: number[] })?.selected?.includes(oi)"
                          @toggle="toggleChoice(q.id, oi, !!parseConfig(q.config).multiSelect)"
                      >
                        {{ opt }}
                      </SelectionToggleButton>
                    </div>
                  </template>
                  <TextInput v-if="parseConfig(q.config).allowOther"
                             v-model="(answers[q.id] as { selected: number[]; other: string }).other"
                             placeholder="Sonstiges..." class="mt-2" />
                </div>
              </template>

              <!-- TEXT -->
              <template v-if="q.questionType === QuestionTypes.TEXT">
                <TextAreaInput v-if="parseConfig(q.config).longAnswer"
                               v-model="(answers[q.id] as { text: string }).text" />
                <TextInput v-else v-model="(answers[q.id] as { text: string }).text" />
              </template>

              <!-- RATING -->
              <template v-if="q.questionType === QuestionTypes.RATING">
                <div class="flex gap-1">
                  <template v-if="(parseConfig(q.config).icon as string) !== 'NUMBER'">
                    <IconButton v-for="n in (parseConfig(q.config).scale as number || 5)" :key="n"
                            :icon="ratingIcon(parseConfig(q.config).icon as string)"
                            :label="`Rating ${n}`"
                            :class="n <= ((answers[q.id] as { rating: number })?.rating ?? 0) ? 'text-primary' : 'text-(--text-muted)'"
                            class="text-xl hover:text-primary"
                            @click="setRating(q.id, n)" />
                  </template>
                  <template v-else>
                    <SelectionToggleButton v-for="n in (parseConfig(q.config).scale as number || 5)" :key="n"
                            :selected="n <= ((answers[q.id] as { rating: number })?.rating ?? 0)"
                            @toggle="setRating(q.id, n)">
                      {{ n }}
                    </SelectionToggleButton>
                  </template>
                </div>
              </template>

              <!-- DATE -->
              <template v-if="q.questionType === QuestionTypes.DATE">
                <DateInput v-model="(answers[q.id] as { date: string }).date" />
              </template>

              <!-- RANKING -->
              <template v-if="q.questionType === QuestionTypes.RANKING">
                <div class="space-y-1">
                  <div v-for="(optIdx, rank) in ((answers[q.id] as { order: number[] })?.order ?? [])" :key="rank"
                       class="flex items-center gap-2 px-3 py-2 rounded border border-bg-light-accent dark:border-bg-dark-accent">
                    <span class="text-xs text-(--text-muted) w-5">{{ rank + 1 }}.</span>
                    <span class="flex-1 text-sm">{{ (parseConfig(q.config).options as string[])?.[optIdx] }}</span>
                    <IconButton :icon="['fas', 'chevron-up']" :label="'Up'" class="text-(--text-muted) hover:text-primary" @click="moveRankItem(q.id, rank, -1)" />
                    <IconButton :icon="['fas', 'chevron-down']" :label="'Down'" class="text-(--text-muted) hover:text-primary" @click="moveRankItem(q.id, rank, 1)" />
                  </div>
                </div>
              </template>

              <!-- LIKERT -->
              <template v-if="q.questionType === QuestionTypes.LIKERT">
                <div class="overflow-x-auto">
                  <table class="w-full text-sm">
                    <thead>
                    <tr>
                      <th></th>
                      <th v-for="n in ((parseConfig(q.config).scaleMax as number) - (parseConfig(q.config).scaleMin as number) + 1)"
                          :key="n" class="text-center px-2 py-1 text-xs text-(--text-muted)">
                        {{ (parseConfig(q.config).scaleMin as number) + n - 1 }}
                      </th>
                    </tr>
                    </thead>
                    <tbody>
                    <tr v-for="(stmt, si) in (parseConfig(q.config).statements as string[])" :key="si"
                        class="border-t border-bg-light-accent/50 dark:border-bg-dark-accent/50">
                      <td class="py-2 pr-4 text-sm">{{ stmt || `Option ${si + 1}` }}</td>
                      <td v-for="n in ((parseConfig(q.config).scaleMax as number) - (parseConfig(q.config).scaleMin as number) + 1)"
                          :key="n" class="text-center px-2 py-2">
                        <SelectionToggleButton
                               :selected="(answers[q.id] as { ratings: Record<string, number> })?.ratings?.[String(si)] === (parseConfig(q.config).scaleMin as number) + n - 1"
                               @toggle="setLikertRating(q.id, si, (parseConfig(q.config).scaleMin as number) + n - 1)">
                          {{ (parseConfig(q.config).scaleMin as number) + n - 1 }}
                        </SelectionToggleButton>
                      </td>
                    </tr>
                    </tbody>
                  </table>
                </div>
              </template>
            </div>
          </NeutralContainer>
        </div>

        <div class="flex justify-end gap-3">
          <SecondaryButton @click="router.push({ name: 'forms-list' })">{{ t('common.cancel') }}</SecondaryButton>
          <PrimaryButton @click="submit">
            {{ hasExistingResponse ? t('forms.update') : t('forms.submit') }}
          </PrimaryButton>
        </div>
      </template>
    </div>
  </ViewContent>
</template>
