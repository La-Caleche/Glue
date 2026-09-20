/*
 * Glue's own options page.
 *
 * The slider only writes the web scale when it is let go. Applying it while dragging would rescale
 * this very page, which moves the slider out from under the pointer that is dragging it: the value
 * then reads off the new geometry and runs away. The preview carries the live feedback instead — it
 * lays its content out with the room the target scale would give it, then scales it back into a
 * fixed frame, so it shows the real density in both directions.
 */

import { call, close, ready, state } from "https://glue-web.glue/bridge.js";

const panel = document.getElementById("panel");
const offline = document.getElementById("offline");
const follow = document.getElementById("follow");
const slider = document.getElementById("scale");
const value = document.getElementById("value");
const hint = document.getElementById("hint");
const gameScale = document.getElementById("game-scale");
const metrics = document.getElementById("metrics");
const frame = document.getElementById("frame");
const stage = document.getElementById("stage");
const done = document.getElementById("done");

let settings = null;
let pending = null;

const format = (scale) => `${scale.toFixed(2)}×`;

function clamp(scale) {
    if (!settings) return scale;
    return Math.min(settings.maximum, Math.max(settings.minimum, scale));
}

function target() {
    if (pending !== null) return pending;
    if (!settings) return window.devicePixelRatio;
    return settings.followsGameScale ? settings.gameScale : settings.scale;
}

function describe() {
    if (pending !== null) return `Let go to draw every page at ${format(pending)}.`;
    if (settings.followsGameScale) return "Pages follow the game's GUI scale, like vanilla screens.";
    return `Pages stay at ${format(settings.scale)} whatever the game's GUI scale is.`;
}

function render() {
    if (!settings) return;
    slider.min = settings.minimum;
    slider.max = settings.maximum;
    slider.step = settings.step;
    follow.checked = settings.followsGameScale;
    gameScale.textContent = `(${format(settings.gameScale)})`;

    const scale = target();
    if (pending === null) slider.value = String(clamp(scale));
    value.textContent = format(scale);
    hint.textContent = describe();
    renderPreview(scale);
    renderMetrics();
}

/** Lays the sample out at the target density, then fits it back into the frame. */
function renderPreview(scale) {
    const ratio = scale / window.devicePixelRatio;
    stage.style.width = `${100 / ratio}%`;
    stage.style.height = `${frame.clientHeight / ratio}px`;
    stage.style.transform = `scale(${ratio})`;
}

function renderMetrics() {
    const ratio = window.devicePixelRatio;
    metrics.textContent = `${window.innerWidth}×${window.innerHeight} css · `
        + `${Math.round(window.innerWidth * ratio)}×${Math.round(window.innerHeight * ratio)} px · ${format(ratio)}`;
}

function refuse(error) {
    hint.textContent = error instanceof Error ? error.message : String(error);
}

slider.addEventListener("input", () => {
    pending = Number(slider.value);
    render();
});

slider.addEventListener("change", () => {
    const chosen = clamp(Number(slider.value));
    pending = null;
    call("settings.scale", chosen).catch(refuse);
});

follow.addEventListener("change", () => {
    pending = null;
    const request = follow.checked
        ? call("settings.followGameScale")
        : call("settings.scale", clamp(Number(slider.value)));
    request.catch(refuse);
});

done.addEventListener("click", () => close());
window.addEventListener("resize", render);

state("settings", (published) => {
    settings = published;
    render();
});

try {
    await ready;
} catch {
    panel.hidden = true;
    offline.hidden = false;
}
