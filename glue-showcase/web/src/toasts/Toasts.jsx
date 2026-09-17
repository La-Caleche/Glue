import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';
import { on, usePageLifecycle } from '../shared/bridge.js';
import './toasts.css';

function Toast({ toast, dismiss, remove }) {
    useEffect(() => {
        const timer = setTimeout(() => dismiss(toast.id), 4500);
        return () => clearTimeout(timer);
    }, [toast.id, dismiss]);

    return <section className={`toast panel${toast.leaving ? ' is-leaving' : ''}`}
        onAnimationEnd={event => { if (event.animationName === 'leave') remove(toast.id); }}>
        <strong>{toast.title}</strong><span className="muted">{toast.message}</span>
    </section>;
}

export default function Toasts() {
    usePageLifecycle();
    const [toasts, setToasts] = useState([]);
    const nextId = useRef(0);
    const dismiss = useCallback(id => setToasts(current => current.map(toast => toast.id === id ? { ...toast, leaving: true } : toast)), []);
    const remove = useCallback(id => setToasts(current => current.filter(toast => toast.id !== id)), []);

    useLayoutEffect(() => on('toast', message => {
        const toast = { ...message, id: ++nextId.current, leaving: false };
        setToasts(current => {
            const items = [...current, toast];
            const active = items.filter(item => !item.leaving);
            const overflow = new Set(active.slice(0, Math.max(0, active.length - 3)).map(item => item.id));
            return items.map(item => overflow.has(item.id) ? { ...item, leaving: true } : item);
        });
    }), []);

    return toasts.map(toast => <Toast key={toast.id} toast={toast} dismiss={dismiss} remove={remove} />);
}
