import { test } from 'node:test';
import assert from 'node:assert/strict';

import {
    MESSAGES,
    REGISTRATION_ENDPOINT,
    isSecureContext,
    submitRegistration,
    validateRequired,
} from '../../main/resources/static/js/registration.js';

const HTTPS = { protocol: 'https:', hostname: 'shop.example.com' };
const VALUES = { email: ' jane@example.com ', mobile: '+14155552671' };

function jsonResponse(status, body) {
    return { status, json: async () => body };
}

function recordingFetch(response) {
    const calls = [];
    const fetchImpl = async (url, init) => {
        calls.push({ url, init });
        if (response instanceof Error) {
            throw response;
        }
        return response;
    };
    return { calls, fetchImpl };
}

test('validateRequired flags each missing field', () => {
    assert.deepEqual(validateRequired({ email: '', mobile: '  ' }), {
        email: MESSAGES.emailRequired,
        mobile: MESSAGES.mobileRequired,
    });
    assert.deepEqual(validateRequired({ email: 'a@b.co', mobile: undefined }), { mobile: MESSAGES.mobileRequired });
    assert.deepEqual(validateRequired({ email: 'a@b.co', mobile: '123' }), {});
});

test('isSecureContext only allows https or localhost', () => {
    assert.equal(isSecureContext(HTTPS), true);
    assert.equal(isSecureContext({ protocol: 'http:', hostname: 'localhost' }), true);
    assert.equal(isSecureContext({ protocol: 'http:', hostname: 'shop.example.com' }), false);
});

test('does not send anything over an insecure page', async () => {
    const { calls, fetchImpl } = recordingFetch(jsonResponse(201, {}));
    const result = await submitRegistration(VALUES, { fetchImpl, location: { protocol: 'http:', hostname: 'shop.example.com' } });
    assert.equal(result.kind, 'insecure');
    assert.equal(calls.length, 0);
});

test('posts trimmed email and mobile as JSON to the registration endpoint', async () => {
    const { calls, fetchImpl } = recordingFetch(jsonResponse(201, { outcome: 'SUCCESS', user_id: 'u-1', message: 'Account created.' }));
    const result = await submitRegistration(VALUES, { fetchImpl, location: HTTPS });

    assert.equal(calls.length, 1);
    assert.equal(calls[0].url, REGISTRATION_ENDPOINT);
    assert.equal(calls[0].init.method, 'POST');
    assert.deepEqual(JSON.parse(calls[0].init.body), { email: 'jane@example.com', mobile: '+14155552671', client_context: 'web' });
    assert.deepEqual(result, { kind: 'success', userId: 'u-1', message: 'Account created.' });
});

test('maps 422 to server field errors', async () => {
    const { fetchImpl } = recordingFetch(jsonResponse(422, {
        outcome: 'FAILURE',
        message: 'Please correct the highlighted fields.',
        field_errors: { email: 'An account with this email already exists.' },
    }));
    const result = await submitRegistration(VALUES, { fetchImpl, location: HTTPS });
    assert.equal(result.kind, 'field-errors');
    assert.deepEqual(result.fieldErrors, { email: 'An account with this email already exists.' });
});

test('maps 5xx to a non-technical try-again-later message', async () => {
    const { fetchImpl } = recordingFetch(jsonResponse(503, { outcome: 'FAILURE', message: 'x' }));
    const result = await submitRegistration(VALUES, { fetchImpl, location: HTTPS });
    assert.deepEqual(result, { kind: 'unavailable', message: MESSAGES.unavailable });
});

test('handles non-JSON error bodies from proxies', async () => {
    const fetchImpl = async () => ({ status: 502, json: async () => { throw new SyntaxError('bad'); } });
    const result = await submitRegistration(VALUES, { fetchImpl, location: HTTPS });
    assert.equal(result.kind, 'unavailable');
});

test('network failure yields a connection message and is not retried', async () => {
    const { calls, fetchImpl } = recordingFetch(new TypeError('Failed to fetch'));
    const result = await submitRegistration(VALUES, { fetchImpl, location: HTTPS });
    assert.deepEqual(result, { kind: 'connection', message: MESSAGES.connection });
    assert.equal(calls.length, 1);
});

test('times out a hung request as a connection issue without retrying', async () => {
    let attempts = 0;
    const fetchImpl = (url, init) => {
        attempts += 1;
        return new Promise((resolve, reject) => {
            init.signal.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')));
        });
    };
    const result = await submitRegistration(VALUES, { fetchImpl, location: HTTPS, timeoutMs: 20 });
    assert.equal(result.kind, 'connection');
    assert.equal(attempts, 1);
});

test('other client errors produce a generic non-technical message', async () => {
    const { fetchImpl } = recordingFetch(jsonResponse(400, { outcome: 'FAILURE', message: 'Secure connection required.' }));
    const result = await submitRegistration(VALUES, { fetchImpl, location: HTTPS });
    assert.deepEqual(result, { kind: 'rejected', message: MESSAGES.invalidRequest });
});
