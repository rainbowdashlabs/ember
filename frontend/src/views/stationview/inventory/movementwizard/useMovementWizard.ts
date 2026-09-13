/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref} from 'vue'
import {inventory, movements} from '@/api'
import {MovementPurpose, type FlowPreview, type MovementPurposeName} from '@/api/movements'
import type {Inventory, InventorySize} from '@/api/inventory'

/** Which question the wizard is on. The preview is always last, because it is the answer to the rest. */
export type WizardStep = 'purpose' | 'party' | 'subject' | 'reason' | 'preview'

/**
 * What a caller already knows when it opens the wizard from a screen of its own.
 *
 * <p>A piece on a member's list answers three of the four questions by itself, so the wizard asks the
 * one that is left. The steps named in {@link skip} are not asked at all.
 */
export interface WizardPrefill {
    purpose?: MovementPurposeName
    memberId?: number | null
    itemId?: number | null
    inventoryId?: number | null
    oldSizeId?: number | null
    newSizeId?: number | null
    /** The self-check this was raised during, recorded against that task. */
    selfCheckId?: number | null
    skip?: WizardStep[]
    /**
     * Acknowledge the step the new Vorgang lands on straight away. What the rapid check does: the
     * member is standing there with the piece, so taking it in is the same action as raising the swap.
     */
    advanceOnce?: boolean
}

/** A piece leaves on a return and a swap; on an issue or a request there is nothing to hand in. */
export function startsFromAPiece(purpose: MovementPurposeName | null): boolean {
    return purpose === MovementPurpose.RETURN || purpose === MovementPurpose.EXCHANGE
}

/**
 * The wizard's state: what has been answered, which question comes next, and what the answers would
 * set going.
 *
 * <p>The chain is resolved as soon as the subject is known, so the last step can draw it rather than
 * discovering on submit that no chain serves the combination.
 */
export function useMovementWizard(prefill: () => WizardPrefill) {
    const purpose = ref<MovementPurposeName | null>(null)
    const memberId = ref<number | null>(null)
    const forTheStore = ref(false)
    const itemId = ref<number | null>(null)
    const inventoryId = ref<number | null>(null)
    const oldSizeId = ref<number | null>(null)
    const newSizeId = ref<number | null>(null)
    const reason = ref('')

    const inventories = ref<Inventory[]>([])
    const sizes = ref<InventorySize[]>([])
    const ownerAbove = ref<string | null>(null)

    const preview = ref<FlowPreview | null>(null)
    const resolved = ref(false)
    const busy = ref(false)

    const step = ref<WizardStep>('purpose')

    /** The questions this wizard asks, the prefilled ones left out. */
    const steps = computed<WizardStep[]>(() => {
        const skip = prefill().skip ?? []
        const all: WizardStep[] = ['purpose', 'party', 'subject', 'reason', 'preview']
        return all.filter(name => !skip.includes(name))
    })

    const position = computed(() => steps.value.indexOf(step.value) + 1)
    const total = computed(() => steps.value.length)

    /**
     * The purposes this station can actually start.
     *
     * <p>A station with nobody above it has nobody to ask and nobody to send anything back to, so
     * neither a request nor the chains that end at a body are offered. That is read from the inventory
     * side rather than from the session, which knows nothing about it.
     */
    const purposes = computed<MovementPurposeName[]>(() => {
        const offered: MovementPurposeName[] = [MovementPurpose.ISSUE, MovementPurpose.RETURN, MovementPurpose.EXCHANGE]
        if (ownerAbove.value) offered.push(MovementPurpose.REQUEST)
        return offered
    })

    async function load() {
        const [loadedInventories, owner] = await Promise.all([
            inventory.listInventories(),
            inventory.ownerAbove().catch(() => null),
        ])
        inventories.value = loadedInventories
        ownerAbove.value = owner
    }

    /**
     * Fills in what a caller already knows, and starts on the first question it left open.
     *
     * <p>A caller that names the inventory itself has answered the subject step, so the sizes that step
     * would have loaded are loaded here instead: a swap asks for the wanted size later on.
     */
    async function start() {
        const known = prefill()
        purpose.value = known.purpose ?? null
        memberId.value = known.memberId ?? null
        forTheStore.value = known.memberId === null && (known.skip ?? []).includes('party')
        itemId.value = known.itemId ?? null
        inventoryId.value = known.inventoryId ?? null
        oldSizeId.value = known.oldSizeId ?? null
        newSizeId.value = known.newSizeId ?? null
        reason.value = ''
        preview.value = null
        resolved.value = false
        step.value = steps.value[0] ?? 'preview'
        sizes.value = []
        if (inventoryId.value != null) await loadSizes()
        if (step.value === 'preview') await resolveChain()
    }

    async function loadSizes() {
        if (inventoryId.value == null) {
            sizes.value = []
            return
        }
        try {
            sizes.value = await inventory.listSizes(inventoryId.value)
        } catch {
            sizes.value = []
        }
    }

    /** Asks what chain the answers would walk, which is what the last step draws. */
    async function resolveChain() {
        if (!purpose.value) return
        resolved.value = false
        preview.value = await movements.resolveFlow({
            purpose: purpose.value,
            memberId: memberId.value,
            itemId: itemId.value,
            inventoryId: inventoryId.value,
        })
        resolved.value = true
    }

    const canLeaveSubject = computed(() => {
        if (startsFromAPiece(purpose.value)) return itemId.value != null
        return inventoryId.value != null
    })

    async function next() {
        const order = steps.value
        const index = order.indexOf(step.value)
        const following = order[index + 1]
        if (!following) return
        if (following === 'preview') await resolveChain()
        if (step.value === 'subject') await loadSizes()
        step.value = following
    }

    function back() {
        const order = steps.value
        const index = order.indexOf(step.value)
        const previous = order[index - 1]
        if (previous) step.value = previous
    }

    /** Starts the Vorgang, and walks it one step where the caller asked for that. */
    async function submit(): Promise<number | null> {
        if (!purpose.value) return null
        busy.value = true
        try {
            const detail = await movements.createMovement({
                purpose: purpose.value,
                memberId: memberId.value,
                outgoingItemId: startsFromAPiece(purpose.value) ? itemId.value : null,
                pickedItemId: startsFromAPiece(purpose.value) ? null : itemId.value,
                inventoryId: inventoryId.value,
                oldSizeId: oldSizeId.value,
                newSizeId: newSizeId.value,
                reason: reason.value.trim() || undefined,
                selfCheckId: prefill().selfCheckId ?? null,
            })
            if (prefill().advanceOnce && detail.movement.state === 'OPEN') {
                const standing = detail.steps.find(candidate => candidate.current)
                if (standing?.actionable) {
                    await movements.acknowledgeStep(detail.movement.id, {stepId: standing.id})
                }
            }
            return detail.movement.id
        } finally {
            busy.value = false
        }
    }

    return {
        purpose,
        memberId,
        forTheStore,
        itemId,
        inventoryId,
        oldSizeId,
        newSizeId,
        reason,
        inventories,
        sizes,
        ownerAbove,
        purposes,
        preview,
        resolved,
        busy,
        step,
        steps,
        position,
        total,
        canLeaveSubject,
        load,
        start,
        next,
        back,
        submit,
    }
}
