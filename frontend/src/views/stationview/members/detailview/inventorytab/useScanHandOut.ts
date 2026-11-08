/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref, type Ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {inventory, movements} from '@/api'
import type {InventoryItem} from '@/api/generated/schema'
import type {HandOutMode} from '@/components/inventory/HandOutChoice.vue'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useFlashMessage} from '@/composables/useFlashMessage'
import {normaliseScannedPayload} from '@/components/scanner/useBarcodeScanner'
import {describeFailure, type Failure} from '@/util/failure'

/** A piece as far as handing it over needs it, which a freshly created piece also answers. */
type HandedPiece = Pick<InventoryItem, 'id' | 'name' | 'inventoryId'>

/**
 * Hands a scanned piece to the member, now or as a planned hand-over.
 *
 * <p>A refusal is kept on the screen rather than flashed past, because somebody at a counter with the
 * piece in their hand takes a message that disappears for one they missed reading. A code nobody knows
 * is held as {@link unknownCode}, so the piece can be written down first and handed over after.
 *
 * @param memberId    the member receiving the piece
 * @param memberName  how the member is named on the hand-over record
 * @param handedOver  called after every hand-over, so the lists beside the scanner read again
 */
export function useScanHandOut(memberId: Ref<number>, memberName: Ref<string>, handedOver: () => Promise<void> | void) {
    const {t} = useI18n()

    const scanValue = ref('')
    const handOutMode = ref<HandOutMode>('NOW')
    const unknownCode = ref<string | null>(null)
    const handoverFailure = ref<Failure | null>(null)
    const {message: success, flash} = useFlashMessage(2500)

    async function handOver(item: HandedPiece) {
        if (handOutMode.value === 'PLANNED') {
            await movements.planHandOut(memberId.value, item.id, item.inventoryId)
            flash(t('inventory.handOut.plannedFlash', {name: item.name ?? ''}))
        } else {
            await inventory.assignItem(item.id, {memberId: memberId.value, memberName: memberName.value})
            flash(t('inventory.assign.assigned', {name: item.name ?? ''}))
        }
        await handedOver()
    }

    const {running: busy, failure: scanFailure, run: runScan} = useAsyncAction(async (term: string) => {
        const item = await inventory.findByInternalId(term)
        if (!item) {
            unknownCode.value = term
            return
        }
        if (item.assignedTo === memberId.value) {
            flash(t('memberDetail.scanAlreadyHere', {name: item.name ?? term}))
            return
        }
        await handOver(item)
    })

    /** Follows the code in the input and hands its piece over. */
    async function submit() {
        const term = scanValue.value.trim()
        if (!term) return
        scanValue.value = ''
        handoverFailure.value = null
        await runScan(term)
    }

    /** Follows a code the camera read, unless a scan is still being handled. */
    async function decoded(value: string) {
        if (busy.value) return
        scanValue.value = normaliseScannedPayload(value)
        await submit()
    }

    /** Hands over the piece that was just written down for an unknown code. */
    async function createdFromUnknown(item: InventoryItem) {
        unknownCode.value = null
        handoverFailure.value = null
        try {
            await handOver(item)
        } catch (e) {
            handoverFailure.value = describeFailure(e, t)
        }
    }

    return {scanValue, handOutMode, unknownCode, busy, scanFailure, handoverFailure, success, submit, decoded, createdFromUnknown}
}
