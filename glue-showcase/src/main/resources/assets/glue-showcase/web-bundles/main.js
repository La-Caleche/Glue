import { call, ready, state } from 'https://glue-web.glue/bridge.js';

const error = document.querySelector('#error');
state('bundle', value => {
    document.querySelector('#status').textContent = JSON.stringify(value, null, 2);
    document.querySelector('[data-action=activate]').disabled = !value.ready;
    error.textContent = value.error ?? '';
});
for (const button of document.querySelectorAll('[data-action]')) {
    button.onclick = () => call(`bundle.${button.dataset.action}`).catch(failure => { error.textContent = failure.message; });
}
document.querySelector('#lazy').onclick = async () => {
    const module = await import('./release.js');
    document.querySelector('#module').textContent = module.description;
};
await ready;
document.documentElement.dataset.connected = 'true';
