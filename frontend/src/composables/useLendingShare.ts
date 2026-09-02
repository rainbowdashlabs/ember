/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, toValue, type ComputedRef, type MaybeRefOrGetter} from 'vue'
import {useI18n} from 'vue-i18n'
import {useInventoryRoutes} from '@/composables/useInventoryRoutes'
import {useSession} from '@/composables/useSession'
import {ShareGrant, ShareScope, type ShareSetting} from '@/api/lending'
import {StationPermission} from '@/api/types'

/**
 * Whether the sharing controls belong on this screen at all, and how the current setting reads.
 *
 * <p>Three questions decide whether they are shown, and all three have to be yes. Only somebody who
 * may manage lending decides what goes out; only a station lends, so the same inventory screens
 * shown for an association name no lending route and offer nothing; and only gear the station owns
 * can be offered, so a screen standing on somebody else's gear shows no control rather than a
 * control that would be refused.
 *
 * @param lendable whether the gear this screen stands on is the station's to lend, where the screen
 *                 knows. Screens that cannot be about somebody else's gear leave it out.
 */
export function useLendingShare(lendable?: MaybeRefOrGetter<boolean>): {
    visible: ComputedRef<boolean>
    stateLabel: (setting: ShareSetting | null | undefined) => string
} {
    const routes = useInventoryRoutes()
    const {hasPermission} = useSession()
    const {t} = useI18n()

    const visible = computed(() =>
        Boolean(routes.lendingShares)
        && hasPermission(StationPermission.INVENTORY_LENDING_MANAGER)
        && toValue(lendable) !== false,
    )

    function stateLabel(setting: ShareSetting | null | undefined): string {
        if (!setting?.shared) return t('lendingShare.stateUnshared')
        if (setting.grant === ShareGrant.WITHHOLD) return t('lendingShare.stateWithheld')
        if (setting.scope === ShareScope.SPECIFIC) return t('lendingShare.stateNamedPartners')
        return t('lendingShare.stateAllPartners')
    }

    return {visible, stateLabel}
}
