/*
 * Glue Web page bridge: import it from https://glue-web.glue/bridge.js inside a Glue surface.
 * It connects once per document to a host that trusts the page's origin, then exposes the host's
 * Java actions, published state, events and native slots.
 */
const channel = window.glueQuery;
const pending = new Map();
const states = new Map();
const stateListeners = new Map();
const eventListeners = new Map();
let nextId = 1;
let lastSlots = '';
let settle;

/** Resolves once the host accepted this document and published its initial state. */
export const ready = new Promise((resolve, reject) => {
    settle = { resolve, reject };
});
ready.catch(() => {});

function bridgeError(code, message) {
    const error = new Error(message);
    error.name = 'GlueBridgeError';
    error.code = code;
    return error;
}

function send(message) {
    return new Promise((resolve, reject) => {
        if (typeof channel !== 'function') {
            reject(bridgeError(0, 'The Glue bridge is only available inside a Glue web surface'));
            return;
        }
        const id = nextId++;
        pending.set(id, { resolve, reject });
        try {
            channel({
                request: JSON.stringify({ ...message, id }),
                onSuccess() {},
                onFailure(code, text) {
                    pending.delete(id);
                    reject(bridgeError(code, text));
                },
            });
        } catch (error) {
            pending.delete(id);
            reject(error);
        }
    });
}

function notify(listeners, value) {
    if (!listeners) return;
    for (const listener of [...listeners]) {
        try {
            listener(value);
        } catch (error) {
            reportError(error);
        }
    }
}

function applyStates(revision, values) {
    for (const [key, value] of Object.entries(values)) {
        const current = states.get(key);
        if (current && current.revision > revision) continue;
        states.set(key, { revision, value });
        notify(stateListeners.get(key), value);
    }
}

function receive(message) {
    if (message.t === 'result') {
        const request = pending.get(message.id);
        if (!request) return;
        pending.delete(message.id);
        if (message.ok) request.resolve(message.value);
        else request.reject(bridgeError(message.code, message.error));
    } else if (message.t === 'state') {
        applyStates(message.revision, message.states);
    } else if (message.t === 'event') {
        notify(eventListeners.get(message.name), message.data);
    }
}

Object.defineProperty(window, '__glueBridge', { value: Object.freeze({ receive }) });

function subscribe(registry, key, listener) {
    if (typeof listener !== 'function') throw new TypeError('listener must be a function');
    let listeners = registry.get(key);
    if (!listeners) {
        listeners = new Set();
        registry.set(key, listeners);
    }
    listeners.add(listener);
    return () => listeners.delete(listener);
}

/** Calls a Java @WebAction with a JSON payload and resolves with its JSON result. */
export function call(name, payload = null) {
    return send({ t: 'call', name, payload });
}

/** Listens to a published state key. A known value is delivered first, asynchronously. */
export function state(key, listener) {
    const unsubscribe = subscribe(stateListeners, key, listener);
    if (states.has(key)) {
        queueMicrotask(() => {
            if (stateListeners.get(key)?.has(listener)) listener(states.get(key).value);
        });
    }
    return unsubscribe;
}

/** The latest value of a state key, or undefined before it is published. */
export function snapshot(key) {
    return states.get(key)?.value;
}

/** Listens to events the host sends with emit(event, data). */
export function on(event, listener) {
    return subscribe(eventListeners, event, listener);
}

/** Asks the host to close; screens and widgets support it. */
export function close() {
    return send({ t: 'close' });
}

function round(value) {
    return Math.round(value * 100) / 100;
}

function trackSlots() {
    const slots = [];
    for (const element of document.querySelectorAll('[data-glue-slot]')) {
        if (!element.checkVisibility({ opacityProperty: true, visibilityProperty: true })) continue;
        const bounds = element.getBoundingClientRect();
        if (bounds.width <= 0 || bounds.height <= 0) continue;
        slots.push([element.dataset.glueSlot, round(bounds.x), round(bounds.y), round(bounds.width), round(bounds.height)]);
    }
    const serialized = JSON.stringify(slots);
    if (serialized !== lastSlots) {
        lastSlots = serialized;
        send({ t: 'slots', slots }).catch(() => {
            lastSlots = '';
        });
    }
    requestAnimationFrame(trackSlots);
}

send({ t: 'hello' }).then(connection => {
    applyStates(connection.revision, connection.states);
    settle.resolve();
    requestAnimationFrame(trackSlots);
}, error => settle.reject(error));
