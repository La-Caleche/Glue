import { useState } from 'react';

export function useActionStatus() {
    const [status, setStatus] = useState({ text: '', error: false });

    async function run(action, success) {
        try {
            const result = await action();
            if (success) setStatus({ text: success(result), error: false });
            return result;
        } catch (error) {
            setStatus({ text: error.message, error: true });
        }
    }

    return { status, run };
}

export function Status({ status }) {
    return <p id="status" className={`status${status.error ? ' is-error' : ''}`} role="status">{status.text}</p>;
}

export function formatDistance(blocks) {
    if (blocks < 0) return 'Other dimension';
    return blocks >= 1000 ? `${(blocks / 1000).toFixed(1)} km` : `${blocks} m`;
}
