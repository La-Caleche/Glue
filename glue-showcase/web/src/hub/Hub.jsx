import { call, useGameState, usePageLifecycle } from '../shared/bridge.js';
import { Status, useActionStatus } from '../shared/Status.jsx';
import './hub.css';

const demos = [
    { name: 'lab', title: 'Input lab', detail: 'Plain HTML/JS: typing, sliders, selects and calls into Java.' },
    { name: 'waypoints', title: 'Waypoints', detail: 'React screens with a stacked removal dialog.' },
    { name: 'browser', title: 'Browser', detail: 'A vanilla toolbar around an untrusted web widget.' },
];
const layerNames = { hud: 'Vitals and hotbar', minimap: 'Minimap', toasts: 'Toasts' };
const scenes = [
    { name: 'orbit', title: 'Orbit scene', detail: 'Rotate, pan and zoom around nearby blocks.' },
    { name: 'fps', title: 'FPS scene', detail: 'Fly through a block and entity preview with mouse capture.' },
    { name: 'gizmo', title: 'Gizmo scene', detail: 'Pick and transform preview blocks, with snap and undo/redo.' },
];

export default function Hub() {
    usePageLifecycle();
    const layers = useGameState('layers');
    const { status, run } = useActionStatus();

    return <main className="hub panel">
        <header>
            <div className="mark" aria-hidden="true" />
            <div>
                <h1>Glue Web</h1>
                <p className="muted">React interfaces and a vanilla input lab, at one CSS pixel per GUI pixel.</p>
            </div>
        </header>
        <hr className="rule" />
        <section className="pages needs-bridge" aria-labelledby="pages-title">
            <h2 id="pages-title">Open a page</h2>
            {demos.map(demo => <button key={demo.name} className="page" data-demo={demo.name}
                onClick={() => run(() => call('demo.open', { name: demo.name }))}>
                <strong>{demo.title}</strong><span className="muted">{demo.detail}</span>
            </button>)}
        </section>
        <section className="layers needs-bridge" aria-labelledby="layers-title">
            <h2 id="layers-title">Layers</h2>
            {Object.entries(layerNames).map(([layer, label]) => <label key={layer} className="switch">
                <input type="checkbox" data-layer={layer} checked={layers?.[layer] ?? false}
                    onChange={event => {
                        const enabled = event.target.checked;
                        run(() => call('layers.set', { layer, enabled }), () => `${label} ${enabled ? 'shown' : 'hidden'}`);
                    }} /> {label}
            </label>)}
            <button className="button" id="toast"
                onClick={() => run(() => call('toast.sample'), () => 'Toast sent')}>Send a toast</button>
        </section>
        <section className="scene-links needs-bridge" aria-labelledby="scenes-title">
            <h2 id="scenes-title">3D scenes · join a world first</h2>
            {scenes.map(scene => <button key={scene.name} className="button" data-scene={scene.name} title={scene.detail}
                onClick={() => run(() => call('demo.open', { name: scene.name }))}>{scene.title}</button>)}
        </section>
        <hr className="rule" />
        <footer><span className="muted">Esc closes. F5 reloads this page.</span><Status status={status} /></footer>
    </main>;
}
