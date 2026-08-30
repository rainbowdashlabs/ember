/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup generic="K extends string | number">
import {computed, ref, watch, type Ref} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import Alert from '@/components/feedback/Alert.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import EventFieldValueInput from './EventFieldValueInput.vue'
import type {EventRegistrationField, RegistrationFieldValue} from '@/api/events'
import type {AnswerablePerson, PersonAnswer} from '@/util/eventAnswers'

/**
 * Answering for a household in one go.
 *
 * <p>A guardian with two children and a membership of their own used to walk the same screen three
 * times. Here they tick who the answer is for and give it once.
 *
 * <p>Where the event asks questions, each person gets a tile of their own, because the answers are theirs
 * rather than the household's: one child's shirt size is not the other's. Declining asks nothing, so the
 * tiles are absent then and the dialog is a list of names.
 */
const props = defineProps<{
    people: AnswerablePerson<K>[]
    /** The questions the event asks. Empty when it asks none, and ignored when declining. */
    fields: EventRegistrationField[]
    /** Whether this dialog is saying yes. Saying no needs no answers. */
    attending: boolean
    busy?: boolean
    error?: string
}>()

const show = defineModel<boolean>({required: true})

const emit = defineEmits<{
    confirm: [answers: PersonAnswer<K>[]]
}>()

const {t} = useI18n()

const chosen = ref([]) as Ref<K[]>
const answers = ref(new Map()) as Ref<Map<K, Record<number, string>>>

const asksQuestions = computed(() => props.attending && props.fields.length > 0)

const missing = computed(() => {
    if (!asksQuestions.value) return []
    return chosen.value.filter(key => props.fields.some(
        field => field.config?.required && !(answers.value.get(key)?.[field.id] ?? '').trim()))
})

/** Everyone is ticked when the dialog opens: answering for the whole household is the common case. */
watch(show, (visible) => {
    if (!visible) return
    chosen.value = props.people.map(person => person.key)
    answers.value = new Map(props.people.map(person => [person.key, {}]))
})

function toggle(key: K) {
    chosen.value = chosen.value.includes(key)
        ? chosen.value.filter(entry => entry !== key)
        : [...chosen.value, key]
}

function nameOf(key: K): string {
    return props.people.find(person => person.key === key)?.name ?? String(key)
}

function answerFor(key: K, fieldId: number): string {
    return answers.value.get(key)?.[fieldId] ?? ''
}

function setAnswer(key: K, fieldId: number, value: string) {
    const next = new Map(answers.value)
    next.set(key, {...next.get(key), [fieldId]: value})
    answers.value = next
}

function confirm() {
    if (chosen.value.length === 0 || missing.value.length > 0) return
    emit('confirm', chosen.value.map(key => ({
        key,
        fields: props.fields.map(field => ({fieldId: field.id, value: answerFor(key, field.id)})),
    })))
}
</script>

<template>
    <Modal v-model="show" size="lg">
        <SubHeader class="mb-1">
            {{ attending ? t('events.answerFor') : t('events.declineFor') }}
        </SubHeader>
        <p class="mb-3 text-xs text-(--text-muted)">
            {{ t('events.answerForHint') }}
            <template v-if="asksQuestions"> {{ t('events.answerForFieldsHint') }}</template>
        </p>

        <Alert v-if="error" variant="error" class="mb-3">{{ error }}</Alert>

        <div class="space-y-1 mb-4">
            <label
                v-for="person in people"
                :key="person.key"
                class="flex items-center gap-2 text-sm"
            >
                <input
                    type="checkbox"
                    :checked="chosen.includes(person.key)"
                    :data-testid="`answer-for-${person.key}`"
                    @change="toggle(person.key)"
                />
                {{ person.name }}
            </label>
        </div>

        <div v-if="asksQuestions" class="space-y-3 mb-4">
            <NeutralContainer v-for="key in chosen" :key="key" class="space-y-2">
                <FieldLabel>{{ nameOf(key) }}</FieldLabel>
                <div v-for="field in fields" :key="field.id" class="space-y-1">
                    <FieldLabel>{{ field.name }}{{ field.config?.required ? ' *' : '' }}</FieldLabel>
                    <EventFieldValueInput
                        :field-type="field.fieldType"
                        :config="{...field.config}"
                        :model-value="answerFor(key, field.id)"
                        @update:model-value="value => setAnswer(key, field.id, value)"
                    />
                </div>
            </NeutralContainer>
        </div>

        <div class="flex justify-end gap-2">
            <SecondaryButton @click="show = false">{{ t('common.cancel') }}</SecondaryButton>
            <PrimaryButton
                :disabled="busy || chosen.length === 0 || missing.length > 0"
                data-testid="answer-confirm"
                @click="confirm"
            >
                {{ t('common.save') }}
            </PrimaryButton>
        </div>
    </Modal>
</template>
