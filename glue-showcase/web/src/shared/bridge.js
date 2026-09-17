import { useCallback, useEffect, useSyncExternalStore } from 'react';
import { ready, snapshot, state } from 'https://glue-web.glue/bridge.js';

export { call, close, on } from 'https://glue-web.glue/bridge.js';

/** React owns rendering; the library remains a framework-independent external store. */
export function useGameState(key) {
    const subscribe = useCallback(listener => state(key, listener), [key]);
    const read = useCallback(() => snapshot(key), [key]);
    return useSyncExternalStore(subscribe, read);
}

export function usePageLifecycle() {
    useEffect(() => {
        document.documentElement.dataset.renderer = 'react';
        const reload = event => {
            if (event.key !== 'F5') return;
            event.preventDefault();
            location.reload();
        };
        addEventListener('keydown', reload);
        return () => removeEventListener('keydown', reload);
    }, []);
}

ready.then(
    () => { document.documentElement.dataset.bridge = 'connected'; },
    () => { document.documentElement.dataset.bridge = 'offline'; },
);
