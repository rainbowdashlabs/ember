/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref} from 'vue'
import type {
    FillInResponse,
    WitnessFieldResponse,
    WitnessOffersResponse,
    WitnessPersonResponse,
} from '@/api/generated/schema'
import {
    completeWitnessedSigning,
    generateForWitness,
    getWitnessField,
    getWitnessFillIns,
    getWitnessOffers,
    startWitnessedSigning,
} from '@/api/witnessSigning'
import {useSigningAct} from '../signingview/useBatchSigning'
import {fillInEntries, fillInsComplete, type FillInValues} from '../signingview/fillIns'
import type {SignatureDraft} from '@/util/signatureDraft'
import {afterChoosing, personOf, type WitnessStep} from './witnessFlow'

/**
 * Signing in front of a witness, from picking the person to the witness's confirmation.
 *
 * <p>The witness picks the person, then a document: an open field of theirs, or a template the document is
 * made from on the spot. Where the field names several people, the witness picks who stands in front of
 * them. Then the device is handed over: the person reads, ticks, fills in and draws, and hands it back. The
 * witness confirms with their own account. Going back keeps what was ticked, typed and drawn, until another
 * field is chosen.
 */
export function useWitnessFlow() {
    const step = ref<WitnessStep>('person')
    const person = ref<WitnessPersonResponse | null>(null)
    const offers = ref<WitnessOffersResponse | null>(null)
    const field = ref<WitnessFieldResponse | null>(null)
    const signer = ref<WitnessPersonResponse | null>(null)
    const fillIns = ref<FillInResponse[]>([])
    const values = ref<FillInValues>({})
    const agreed = ref(false)
    const draft = ref<SignatureDraft | null>(null)
    const abandoned = ref(false)

    const entries = () => fillInEntries(fillIns.value, values.value)

    const act = useSigningAct({
        start: () => startWitnessedSigning(requireField().fieldId, requireSigner().memberId, entries()),
        boundTo: () => JSON.stringify([field.value?.fieldId, signer.value?.memberId, entries()]),
        complete: completeWitnessedSigning,
    })

    function requireField(): WitnessFieldResponse {
        if (!field.value) throw new Error('No field is chosen')
        return field.value
    }

    function requireSigner(): WitnessPersonResponse {
        if (!signer.value) throw new Error('Nobody is chosen to sign')
        return signer.value
    }

    const readReady = computed(() => agreed.value && fillInsComplete(fillIns.value, values.value))

    /** Picks the person and loads what may be collected from them. */
    async function pickPerson(picked: WitnessPersonResponse) {
        offers.value = await getWitnessOffers(picked.memberId)
        person.value = picked
        step.value = 'document'
    }

    /** Opens a field from its document's tile, the person being the one the document is about. */
    async function openField(fieldId: number) {
        const opened = await getWitnessField(fieldId)
        person.value = personOf(opened)
        await choose(opened)
    }

    /** Makes a document from a template for the person, and chooses its field where it has one. */
    async function generate(templateId: number) {
        const current = person.value
        if (!current) return
        const made = await generateForWitness(current.memberId, templateId)
        offers.value = await getWitnessOffers(current.memberId)
        const [only, ...others] = made
        if (only && others.length === 0) await choose(only)
    }

    /** Chooses a field to collect, starting afresh with what is ticked, typed and drawn. */
    async function choose(chosen: WitnessFieldResponse) {
        const next = afterChoosing(chosen)
        if (!next) return
        fillIns.value = await getWitnessFillIns(chosen.fieldId)
        field.value = chosen
        signer.value = next.signer
        values.value = {}
        agreed.value = false
        draft.value = null
        abandoned.value = false
        step.value = next.step
    }

    /** Picks who stands in front of the witness, among those the field names. */
    function pickSigner(picked: WitnessPersonResponse) {
        signer.value = picked
        step.value = 'handOver'
    }

    /** The person gives the device back without signing; the witness takes it and chooses again. */
    function abandon() {
        abandoned.value = true
        step.value = 'takeBack'
    }

    /** The witness has the device back: on to the confirmation, or back to the documents after an abandon. */
    function takenBack() {
        step.value = abandoned.value ? 'document' : 'confirm'
    }

    /** The pictures of the confirmation: none of the witness's own, the one the person drew. */
    function pictures() {
        const drawn = draft.value
        const signing = signer.value
        return {
            holder: {draft: null, useSaved: false, keep: false},
            members: signing && drawn ? {[signing.memberId]: drawn} : {},
        }
    }

    /** Starts over with another person, once a signature is collected. */
    function nextPerson() {
        act.reset()
        person.value = null
        offers.value = null
        field.value = null
        signer.value = null
        step.value = 'person'
    }

    /** Collects another signature from the same person. */
    async function samePerson() {
        const current = person.value
        if (!current) return nextPerson()
        act.reset()
        field.value = null
        signer.value = null
        await pickPerson(current)
    }

    return {
        step,
        person,
        offers,
        field,
        signer,
        fillIns,
        values,
        agreed,
        draft,
        abandoned,
        readReady,
        act,
        go: (next: WitnessStep) => {
            step.value = next
        },
        pickPerson,
        openField,
        generate,
        choose,
        pickSigner,
        abandon,
        takenBack,
        pictures,
        nextPerson,
        samePerson,
    }
}
