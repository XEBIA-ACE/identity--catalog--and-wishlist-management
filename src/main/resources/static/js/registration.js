export const REGISTRATION_ENDPOINT = '/api/v1/users/register';
export const DEFAULT_TIMEOUT_MS = 15000;

export const MESSAGES = Object.freeze({
    emailRequired: 'Email is required.',
    mobileRequired: 'Mobile number is required.',
    unavailable: "We couldn't complete your registration right now. Please try again later.",
    connection: "We couldn't complete your registration because of a connection issue. Check your connection and try again.",
    insecure: "Registration is only available over a secure (HTTPS) connection.",
    invalidRequest: "We couldn't complete your registration. Please check your details and try again.",
});

const LOCAL_HOSTS = new Set(['localhost', '127.0.0.1', '[::1]']);

export function validateRequired({ email, mobile }) {
    const errors = {};
    if (!email || !email.trim()) {
        errors.email = MESSAGES.emailRequired;
    }
    if (!mobile || !mobile.trim()) {
        errors.mobile = MESSAGES.mobileRequired;
    }
    return errors;
}

export function isSecureContext(location) {
    return location.protocol === 'https:' || LOCAL_HOSTS.has(location.hostname);
}

export async function submitRegistration(
    { email, mobile },
    { fetchImpl = globalThis.fetch, timeoutMs = DEFAULT_TIMEOUT_MS, endpoint = REGISTRATION_ENDPOINT, location = globalThis.location } = {},
) {
    if (location && !isSecureContext(location)) {
        return { kind: 'insecure', message: MESSAGES.insecure };
    }

    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);
    let response;
    try {
        response = await fetchImpl(endpoint, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
            body: JSON.stringify({ email: email.trim(), mobile: mobile.trim(), client_context: 'web' }),
            credentials: 'same-origin',
            signal: controller.signal,
        });
    } catch {
        return { kind: 'connection', message: MESSAGES.connection };
    } finally {
        clearTimeout(timer);
    }

    const body = await readJson(response);

    if (response.status === 201 && body?.outcome === 'SUCCESS') {
        return { kind: 'success', userId: body.user_id, message: body.message };
    }
    if (response.status === 422 && body?.field_errors) {
        return { kind: 'field-errors', fieldErrors: body.field_errors, message: body.message };
    }
    if (response.status >= 500) {
        return { kind: 'unavailable', message: MESSAGES.unavailable };
    }
    return { kind: 'rejected', message: MESSAGES.invalidRequest };
}

async function readJson(response) {
    try {
        return await response.json();
    } catch {
        return null;
    }
}
