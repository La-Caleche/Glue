import { useGameState, usePageLifecycle } from '../shared/bridge.js';
import './vitals.css';

const percent = (value, maximum = 1) => `${Math.max(0, Math.min(1, maximum > 0 ? value / maximum : 0)) * 100}%`;

function Gauge({ id, tone = id, value, maximum, label = value, mirrored, hidden, invisible, low, children }) {
    return <div id={id} className={`gauge ${tone}${mirrored ? ' is-mirrored' : ''}${low ? ' is-low' : ''}`}
        hidden={hidden} style={{ '--value': percent(value, maximum), visibility: invisible ? 'hidden' : undefined }}>
        <b id={`${id}-value`}>{label}</b><div className="bar"><div className="fill" />{children}</div>
    </div>;
}

export default function Vitals() {
    usePageLifecycle();
    const vitals = useGameState('vitals');
    if (!vitals) return null;
    const total = vitals.maxHealth + vitals.absorption;
    const food = vitals.mount ? { tone: 'mount', value: vitals.mount.health, maximum: vitals.mount.maxHealth }
        : { tone: 'food', value: vitals.food, maximum: 20 };

    return <div className={`cluster${vitals.survival ? '' : ' creative'}${vitals.hardcore ? ' hardcore' : ''}`} id="cluster">
        <Gauge id="health" value={vitals.health} maximum={total} label={Math.ceil(vitals.health + vitals.absorption)} low={vitals.health <= 4}>
            <div className="extra" style={{ '--offset': percent(vitals.health, total), '--value': percent(vitals.absorption, total) }} />
        </Gauge>
        <Gauge id="food" {...food} label={Math.ceil(food.value)} mirrored />
        <Gauge id="armor" value={vitals.armor} maximum={20} invisible={vitals.armor <= 0} />
        <Gauge id="air" value={vitals.air} maximum={vitals.maxAir} label={Math.ceil(vitals.air / 30)} mirrored
            hidden={!vitals.underwater && vitals.air >= vitals.maxAir} />
        <div className="experience" id="experience" style={{ '--value': percent(vitals.progress) }}>
            <div className="bar"><div className="fill" /></div><b id="level" hidden={vitals.level === 0}>{vitals.level}</b>
        </div>
        <div className="hotbar" id="hotbar">
            <div className="tile offhand" data-glue-slot="item:offhand" id="offhand" hidden={!vitals.offhand} />
            {Array.from({ length: 9 }, (_, index) => <div key={index} className={`tile${index === vitals.selected ? ' is-selected' : ''}`}
                data-glue-slot={`item:${index}`}><i>{index + 1}</i></div>)}
        </div>
    </div>;
}
