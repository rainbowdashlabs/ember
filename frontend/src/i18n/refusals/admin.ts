/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ACCOUNT_NOT_HERE, ONE_TIME_PASSWORD_FOR_YOURSELF, ONE_TIME_PASSWORD_PASSKEYS_ONLY} from './shared'

const INSTANCE_UNREACHABLE ='Diese Instanz war nicht erreichbar'

/** What the refusals of instance administration and peers say in German, keyed by their `A-` code. */
export default {
    'A-001': INSTANCE_UNREACHABLE,
    'A-002': INSTANCE_UNREACHABLE,
    'A-003': ACCOUNT_NOT_HERE,
    'A-004': ONE_TIME_PASSWORD_FOR_YOURSELF,
    'A-005': ONE_TIME_PASSWORD_PASSKEYS_ONLY,
}
