import { StrictMode } from 'react';
import { flushSync } from 'react-dom';
import { createRoot } from 'react-dom/client';
import './shared/ui.css';

const pages = {
    hub: () => import('./hub/Hub.jsx'),
    waypoints: () => import('./waypoints/Waypoints.jsx'),
    confirm: () => import('./waypoints/RemovalDialog.jsx'),
    hud: () => import('./hud/Vitals.jsx'),
    minimap: () => import('./minimap/Minimap.jsx'),
    toasts: () => import('./toasts/Toasts.jsx'),
    inventory: () => import('./inventory/FieldNotes.jsx'),
};

const { default: Page } = await pages[document.body.dataset.page]();
// Install page listeners before CEF can deliver events from the initial bridge connection.
flushSync(() => createRoot(document.getElementById('root')).render(<StrictMode><Page /></StrictMode>));
