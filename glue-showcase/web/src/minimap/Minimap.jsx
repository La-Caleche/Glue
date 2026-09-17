import { useGameState, usePageLifecycle } from '../shared/bridge.js';
import './minimap.css';

const hole = 36;

function position(dx, dz, scale) {
    let x = dx * scale;
    let y = dz * scale;
    const distance = Math.hypot(x, y);
    const far = distance > hole - 3;
    if (far) {
        x *= (hole - 3) / distance;
        y *= (hole - 3) / distance;
    }
    return { far, style: { '--x': `${x}px`, '--y': `${y}px` } };
}

export default function Minimap() {
    usePageLifecycle();
    const map = useGameState('map');
    if (!map) return null;
    const scale = hole / map.radius;

    return <>
        <div className="instrument" id="instrument">
            <div className="terrain" data-glue-slot="terrain" />
            <div id="pins">{map.pins.map(pin => {
                const placed = position(pin.dx, pin.dz, scale);
                return <span key={pin.id} className={`pin${placed.far ? ' is-far' : ''}`} title={`${pin.name}, ${pin.distance} m`}
                    style={{ ...placed.style, '--swatch': pin.color }} />;
            })}</div>
            <div className="player" id="player" style={{ ...position(map.playerX, map.playerZ, scale).style,
                '--heading': `${map.heading + 180}deg` }} />
            <div className="bezel" aria-hidden="true" />
            {['N', 'E', 'S', 'W'].map((label, index) => <span key={label}
                className={`cardinal${index === 0 ? ' is-north' : ''}`} style={{ '--angle': `${index * 90}deg` }}>{label}</span>)}
        </div>
        <div className="readout"><span className="biome" id="biome">{map.biome}</span>
            <span className="coordinates" id="coordinates">{`${map.x}  ${map.y}  ${map.z}`}</span></div>
    </>;
}
