/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import settings from '@/data/generated/settings.json'

/** A setting of the backend's configuration file, as `./toolchain.sh be-settings-catalog` writes it. */
export interface Setting {
    configKey: string
    env: string | null
    default: string
}

/** A variable read outside the backend's configuration file: by a container, the frontend or an external program. */
export interface ExternalVariable {
    name: string
    default: string
}

/**
 * One group of the help centre's list of environment variables. A group takes every setting whose key equals one
 * of its prefixes or lies below one, unless another group names a longer prefix of it; a group of variables the
 * configuration file does not know lists them itself.
 */
export interface EnvironmentGroup {
    title: string
    note?: string
    defaultOpen: boolean
    prefixes?: readonly string[]
    variables?: readonly ExternalVariable[]
}

/** One row of a rendered group, with the translation key of its description. */
export interface EnvironmentRow {
    name?: string
    configKey?: string
    default: string
    descriptionKey: string
}

/** Every setting of the backend's configuration file, in the order the backend declares them. */
export const SETTINGS: readonly Setting[] = settings

/** The groups of the list, in the order the page shows them. */
export const ENVIRONMENT_GROUPS: readonly EnvironmentGroup[] = [
    {title: 'helpCenter.basics.configuration.envGroupDb', defaultOpen: true, prefixes: ['database']},
    {title: 'helpCenter.basics.configuration.envGroupApi', defaultOpen: true, prefixes: ['api']},
    {
        title: 'helpCenter.basics.configuration.envGroupMailing',
        note: 'helpCenter.basics.configuration.envGroupMailingNote',
        defaultOpen: true,
        prefixes: ['mailing'],
    },
    {
        title: 'helpCenter.basics.configuration.envGroupMailSmtp',
        note: 'helpCenter.basics.configuration.envGroupMailSmtpNote',
        defaultOpen: false,
        prefixes: ['mailing.provider', 'mailing.smtp', 'mailing.user', 'mailing.password', 'mailing.apiKey', 'mailing.dailySendLimit', 'mailing.attempts', 'mailing.properties', 'mailing.fallbacks'],
    },
    {title: 'helpCenter.basics.configuration.envGroupAuth', defaultOpen: true, prefixes: ['auth']},
    {
        title: 'helpCenter.basics.configuration.envGroupHibp',
        note: 'helpCenter.basics.configuration.envGroupHibpNote',
        defaultOpen: false,
        prefixes: ['auth.hibp'],
    },
    {
        title: 'helpCenter.basics.configuration.envGroupTwoFactor',
        note: 'helpCenter.basics.configuration.envGroupTwoFactorNote',
        defaultOpen: false,
        prefixes: ['auth.twoFactor', 'auth.webauthn', 'auth.passkeys'],
    },
    {
        title: 'helpCenter.basics.configuration.envGroupStorage',
        note: 'helpCenter.basics.configuration.envGroupStorageNote',
        defaultOpen: true,
        prefixes: ['storage'],
    },
    {
        title: 'helpCenter.basics.configuration.envGroupStorageCompress',
        note: 'helpCenter.basics.configuration.envGroupStorageCompressNote',
        defaultOpen: false,
        prefixes: ['storage.compressPresentations', 'storage.compressOfficeDocs', 'storage.compressPdfs', 'storage.compressTextFiles', 'storage.compressThreshold', 'storage.imageVariantsEnabled', 'storage.imageVariantsWidths', 'storage.imageVariantsWebp'],
    },
    {
        title: 'helpCenter.basics.configuration.envGroupStorageBackend',
        note: 'helpCenter.basics.configuration.envGroupStorageBackendNote',
        defaultOpen: false,
        prefixes: ['storage.backend'],
    },
    {
        title: 'helpCenter.basics.configuration.envGroupNetwork',
        note: 'helpCenter.basics.configuration.envGroupNetworkNote',
        defaultOpen: true,
        prefixes: ['network'],
    },
    {
        title: 'helpCenter.basics.configuration.envGroupMetrics',
        note: 'helpCenter.basics.configuration.envGroupMetricsNote',
        defaultOpen: false,
        prefixes: ['metrics'],
    },
    {
        title: 'helpCenter.basics.configuration.envGroupOperations',
        defaultOpen: false,
        prefixes: ['updates', 'changelog', 'attendance', 'knowledgeBase', 'logging'],
    },
    {title: 'helpCenter.basics.configuration.envGroupMailImport', defaultOpen: false, prefixes: ['mailImport']},
    {title: 'helpCenter.basics.configuration.envGroupFederation', defaultOpen: true, prefixes: ['federation']},
    {title: 'helpCenter.basics.configuration.envGroupTheming', defaultOpen: false, prefixes: ['theming']},
    {
        title: 'helpCenter.basics.configuration.envGroupTools',
        note: 'helpCenter.basics.configuration.envGroupToolsNote',
        defaultOpen: false,
        variables: [
            {name: 'TYPST_BIN', default: 'typst'},
            {name: 'PANDOC_BIN', default: 'pandoc'},
            {name: 'QPDF_BIN', default: 'qpdf'},
            {name: 'CWEBP_BIN', default: 'cwebp'},
            {name: 'LIBREOFFICE_BIN', default: 'libreoffice'},
        ],
    },
    {
        title: 'helpCenter.basics.configuration.envGroupFrontend',
        note: 'helpCenter.basics.configuration.envGroupFrontendNote',
        defaultOpen: false,
        variables: [
            {name: 'NUXT_BACKEND_URL', default: 'http://localhost:8080'},
            {name: 'NUXT_CSP_MODE', default: 'enforce'},
            {name: 'NUXT_PUBLIC_GOOGLE_SITE_VERIFICATION', default: '-'},
            {name: 'NITRO_PORT', default: '3000'},
            {name: 'NITRO_HOST', default: '0.0.0.0'},
        ],
    },
    {
        title: 'helpCenter.basics.configuration.envGroupDemo',
        note: 'helpCenter.basics.configuration.envGroupDemoNote',
        defaultOpen: false,
        prefixes: ['demo'],
    },
    {
        title: 'helpCenter.basics.configuration.envGroupDocker',
        note: 'helpCenter.basics.configuration.envGroupDockerNote',
        defaultOpen: false,
        variables: [
            {name: 'EMBER_TAG', default: 'latest'},
            {name: 'EMBER_HOST', default: '-'},
            {name: 'POSTGRES_DB', default: '-'},
            {name: 'POSTGRES_USER', default: '-'},
            {name: 'POSTGRES_PASSWORD', default: '-'},
        ],
    },
]

/**
 * The translation key of a setting's description.
 *
 * @param configKey the dotted key in the configuration file
 * @returns the key below the help centre's configuration page
 */
export function settingDescriptionKey(configKey: string): string {
    return `helpCenter.basics.configuration.settings.${configKey}`
}

/**
 * The translation key of an external variable's description.
 *
 * @param name the variable
 * @returns the key below the help centre's configuration page
 */
export function variableDescriptionKey(name: string): string {
    return `helpCenter.basics.configuration.variables.${name}`
}

/**
 * The group that takes a setting: the one naming the longest prefix the key equals or lies below.
 *
 * @param configKey the dotted key
 * @returns the group, or undefined when no group takes the key
 */
export function groupOf(configKey: string): EnvironmentGroup | undefined {
    let best: {group: EnvironmentGroup; length: number} | undefined
    for (const group of ENVIRONMENT_GROUPS) {
        for (const prefix of group.prefixes ?? []) {
            const matches = configKey === prefix || configKey.startsWith(`${prefix}.`)
            if (matches && prefix.length > (best?.length ?? -1)) best = {group, length: prefix.length}
        }
    }
    return best?.group
}

/**
 * The rows a group shows: the settings it takes in the backend's order, or the variables it lists.
 *
 * @param group the group
 * @returns the rows
 */
export function rowsOf(group: EnvironmentGroup): EnvironmentRow[] {
    if (group.variables) {
        return group.variables.map(variable => ({
            name: variable.name,
            default: variable.default,
            descriptionKey: variableDescriptionKey(variable.name),
        }))
    }
    return SETTINGS.filter(setting => groupOf(setting.configKey) === group).map(setting => ({
        name: setting.env ?? undefined,
        configKey: setting.configKey,
        default: setting.default,
        descriptionKey: settingDescriptionKey(setting.configKey),
    }))
}
