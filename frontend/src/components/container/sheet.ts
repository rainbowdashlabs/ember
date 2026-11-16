/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {InjectionKey} from 'vue'

/**
 * Whether a container sits directly inside a sheet, one container that holds a whole form.
 *
 * <p>A card placed there draws no box of its own and becomes one section of the sheet, set apart from
 * its neighbours by a divider. Only the cards directly inside are flattened: a card inside such a
 * section keeps its box, because there it frames something within the form rather than the form.
 */
export const SHEET_SECTION: InjectionKey<boolean> = Symbol('sheet-section')
