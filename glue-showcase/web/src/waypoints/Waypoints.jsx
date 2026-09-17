import { useState } from 'react';
import { call, close, useGameState, usePageLifecycle } from '../shared/bridge.js';
import { formatDistance, Status, useActionStatus } from '../shared/Status.jsx';
import './waypoints.css';

function WaypointRow({ waypoint, onRemove }) {
    return <li data-id={waypoint.id}>
        <span className="pin" style={{ '--swatch': waypoint.color }} />
        <span className="name">{waypoint.name}</span>
        <span className="muted">{formatDistance(waypoint.distance)}</span>
        <button className="remove" type="button" title={`Remove ${waypoint.name}`}
            aria-label={`Remove ${waypoint.name}`} onClick={() => onRemove(waypoint.id)}>×</button>
        <span className="where">{waypoint.x}, {waypoint.y}, {waypoint.z} in {waypoint.dimension}</span>
    </li>;
}

export default function Waypoints() {
    usePageLifecycle();
    const waypoints = useGameState('waypoints') ?? [];
    const colors = useGameState('colors') ?? [];
    const here = useGameState('here');
    const [name, setName] = useState('');
    const [color, setColor] = useState(null);
    const { status, run } = useActionStatus();

    function add(event) {
        event.preventDefault();
        run(async () => {
            const waypoint = await call('waypoints.create', { name, color: color ?? colors[0] });
            setName('');
            return waypoint;
        }, waypoint => `Added ${waypoint.name}`);
    }

    return <main className="sheet panel">
        <header>
            <div><h1>Waypoints</h1><p className="muted" id="here">{here ? `You are at ${here.position}` : 'Locating you'}</p></div>
            <button className="button" id="close" type="button" onClick={() => run(close)}>Close</button>
        </header>
        <form className="new needs-bridge" id="new" autoComplete="off" onSubmit={add}>
            <input className="field" id="name" maxLength={24} placeholder="Name this spot" spellCheck={false}
                aria-label="Waypoint name" value={name} onChange={event => setName(event.target.value)} />
            <button className="button is-primary" id="add" type="submit">Add</button>
            <fieldset className="colors" id="colors" aria-label="Color">
                {colors.map((choice, index) => <input key={choice} type="radio" name="color" value={choice}
                    checked={(color ?? colors[0]) === choice} style={{ '--swatch': choice }}
                    aria-label={`Color ${index + 1}`} onChange={() => setColor(choice)} />)}
            </fieldset>
        </form>
        <Status status={status} />
        <hr className="rule" />
        <ol id="list" aria-label="Saved waypoints">
            {waypoints.map(waypoint => <WaypointRow key={waypoint.id} waypoint={waypoint}
                onRemove={id => run(() => call('waypoints.askRemoval', { id }))} />)}
        </ol>
        <p className="empty" id="empty" hidden={waypoints.length > 0}>No waypoints yet. Name this spot above to add one.</p>
    </main>;
}
