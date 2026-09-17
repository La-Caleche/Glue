// The no-framework input lab. The bridge itself is served by Glue Web.
import { ready } from 'https://glue-web.glue/bridge.js';

export { call, on, ready, state } from 'https://glue-web.glue/bridge.js';

export const $ = selector => document.querySelector(selector);

export function showStatus(node, message, isError = false) {
    node.textContent = message;
    node.classList.toggle('is-error', isError);
}

ready.then(
    () => { document.documentElement.dataset.bridge = 'connected'; },
    () => { document.documentElement.dataset.bridge = 'offline'; },
);

// F5 reloads the page; with -Dglue.web.source set, edited files apply without restarting.
addEventListener('keydown', event => {
    if (event.key !== 'F5') return;
    event.preventDefault();
    location.reload();
});
