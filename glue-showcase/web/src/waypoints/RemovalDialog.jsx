import { call, close, useGameState, usePageLifecycle } from '../shared/bridge.js';
import { formatDistance, Status, useActionStatus } from '../shared/Status.jsx';
import './removal.css';

export default function RemovalDialog() {
    usePageLifecycle();
    const waypoint = useGameState('removal');
    const { status, run } = useActionStatus();
    const distance = waypoint?.distance < 0 ? 'It is in another dimension' : `It is ${formatDistance(waypoint?.distance ?? 0)} away`;

    return <main className="dialog panel" role="alertdialog" aria-labelledby="title" aria-describedby="detail">
        <h1 id="title">{waypoint ? `Remove ${waypoint.name}?` : 'Remove this waypoint?'}</h1>
        <p className="muted" id="detail">{waypoint
            ? `${distance}, at ${waypoint.x}, ${waypoint.y}, ${waypoint.z}. The minimap stops showing it.`
            : waypoint === undefined ? 'Loading its details' : 'This waypoint was already removed.'}</p>
        <Status status={status} />
        <div className="actions">
            <button className="button" id="keep" type="button" autoFocus onClick={() => run(close)}>Keep it</button>
            <button className="button is-primary is-danger needs-bridge" id="remove" type="button" disabled={!waypoint}
                onClick={() => run(async () => { await call('removal.confirm'); await close(); })}>Remove</button>
        </div>
    </main>;
}
