import React, { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';

function App() {
    const [game, setGame] = useState({ fps: 0, position: 'Waiting for Minecraft' });
    const [count, setCount] = useState(0);
    const [name, setName] = useState('Explorer');
    const [power, setPower] = useState(65);
    const [animate, setAnimate] = useState(false);
    const [effect, setEffect] = useState('Aurora');
    useEffect(() => { window.receiveGameState = setGame; return () => { delete window.receiveGameState; }; }, []);
    window.demoSnapshot = () => ({ count, name, power, animate, effect, fps: game.fps });
    const send = message => window.jcefQuery({ request: JSON.stringify(message), onSuccess() {}, onFailure(code, text) { console.error(text); } });
    return <main>
        <header><strong>CHROMIUM <span>× GLUE</span></strong><div className="badge">JCEF 146 · REACT 19 · OFFSCREEN</div></header>
        <section className="card">
            <div className="eyebrow">INDEPENDENT RENDERER EXPERIMENT</div>
            <h1>A browser, <span>inside your world.</span></h1>
            <p>Native Chromium input, retained browser rendering, damage-aware GPU uploads.</p>
            <div className="metrics">
                <div><small>GAME FRAMERATE</small><strong>{game.fps} <em>FPS</em></strong></div>
                <div><small>REACT STATE</small><strong id="count">{count} <em>clicks</em></strong></div>
                <div><small>POWER</small><strong>{power} <em>%</em></strong></div>
            </div>
            <div className="form-row"><label>CALLSIGN<input id="callsign" value={name} onChange={e => setName(e.target.value)} /></label>
                <button id="increment" onClick={() => { setCount(count + 1); send({ action: 'increment', value: count + 1 }); }}>Increment state →</button></div>
            <label className="power-label">Power: {power}%<input id="power" type="range" min="0" max="100" value={power} onChange={e => setPower(Number(e.target.value))} /></label>
            <div className="actions">
                <button id="animate" onClick={() => setAnimate(!animate)}>{animate ? 'Stop animation' : 'Animate CSS'}</button>
                <button id="greet" onClick={() => send({ action: 'greet', name })}>Send to Client</button>
                <select id="effect" value={effect} onChange={e => setEffect(e.target.value)}><option>Aurora</option><option>Ocean</option><option>Ember</option></select>
            </div>
            <div className="track"><div className={animate ? 'orb animated' : 'orb'} /></div>
            <footer>Hello, {name} · {game.position}</footer>
        </section>
        <aside><div className="eyebrow">RENDER PATH</div><h2>BGRA → GPU</h2><p>Colors and premultiplied alpha stay intact until the fragment shader.</p><p>F7 compares GPU swizzle with a CPU conversion path.</p><p>F8 measures a correlated JS → pixel → upload round trip.</p><div className="gradient" /></aside>
    </main>;
}
createRoot(document.getElementById('root')).render(<App />);
