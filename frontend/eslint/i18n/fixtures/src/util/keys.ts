export const LABELS = {help: 'helpCenter.link', data: 'literal.key'}

export function refusalKey(code: string): string {
    return `refusal.${code}`
}

export function kindKey(kind: string): string {
    return `kinds.${kind}`
}

export function concatenated(t: (key: string) => string, name: string): string {
    return t('concat.' + name)
}
