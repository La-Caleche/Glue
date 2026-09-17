import { Fragment } from 'react';
import { call, useGameState, usePageLifecycle } from '../shared/bridge.js';
import { Status, useActionStatus } from '../shared/Status.jsx';
import './notes.css';

const fields = { position: 'Position', biome: 'Biome', time: 'Time', light: 'Light', weather: 'Weather', waypoints: 'Waypoints' };

export default function FieldNotes() {
    usePageLifecycle();
    const notes = useGameState('notes');
    const { status, run } = useActionStatus();

    return <main className="notes panel">
        <h2>Field notes</h2><hr className="rule" />
        <dl>{Object.entries(fields).map(([key, label]) => <Fragment key={key}>
            <dt>{label}</dt><dd id={key}>{notes?.[key] ?? ''}</dd>
        </Fragment>)}</dl>
        <div className="actions needs-bridge">
            <Status status={status} />
            <button className="button is-primary" id="mark" type="button"
                onClick={() => run(() => call('waypoints.markHere'), name => `Marked ${name}`)}>Mark this spot</button>
            <button className="button" id="open" type="button"
                onClick={() => run(() => call('waypoints.open'))}>Open waypoints</button>
        </div>
    </main>;
}
