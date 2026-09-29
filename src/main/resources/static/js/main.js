import { submitRegistration, validateRequired } from './registration.js';

const FIELDS = ['email', 'mobile'];

const form = document.getElementById('registration-form');
const submitButton = document.getElementById('submit');
const formError = document.getElementById('form-error');
const registrationSection = document.getElementById('registration');
const successSection = document.getElementById('registration-success');

let inFlight = false;

function showFieldErrors(errors) {
    for (const field of FIELDS) {
        const input = document.getElementById(field);
        const message = document.getElementById(`${field}-error`);
        const error = errors[field];
        input.setAttribute('aria-invalid', error ? 'true' : 'false');
        message.textContent = error ?? '';
        message.hidden = !error;
    }
    const firstInvalid = FIELDS.find((field) => errors[field]);
    if (firstInvalid) {
        document.getElementById(firstInvalid).focus();
    }
}

function showFormError(message) {
    formError.textContent = message ?? '';
    formError.hidden = !message;
}

function clearErrors() {
    showFieldErrors({});
    showFormError(null);
}

function showSuccess() {
    clearErrors();
    registrationSection.hidden = true;
    successSection.hidden = false;
    successSection.focus();
}

form.addEventListener('submit', async (event) => {
    event.preventDefault();
    if (inFlight) {
        return;
    }

    const values = { email: form.email.value, mobile: form.mobile.value };
    const requiredErrors = validateRequired(values);
    showFormError(null);
    showFieldErrors(requiredErrors);
    if (Object.keys(requiredErrors).length > 0) {
        return;
    }

    inFlight = true;
    submitButton.disabled = true;
    try {
        const result = await submitRegistration(values);
        switch (result.kind) {
            case 'success':
                showSuccess();
                break;
            case 'field-errors':
                showFieldErrors(result.fieldErrors);
                showFormError(result.message);
                break;
            default:
                showFormError(result.message);
        }
    } finally {
        inFlight = false;
        submitButton.disabled = false;
    }
});
