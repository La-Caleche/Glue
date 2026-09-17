import assert from 'node:assert/strict';
import { once } from 'node:events';
import { createServer } from 'node:http';
import test from 'node:test';
import { deployDocumentation } from './portainer.mjs';

const image = `registry.example/glue/docs@sha256:${'a'.repeat(64)}`;
const oldImage = `registry.example/glue/docs@sha256:${'b'.repeat(64)}`;
const compose = 'services:\n  docs:\n    image: ${DOCS_IMAGE}\n    labels:\n      custom: keep-me\n';
const originalEnv = [{ name: 'DOCS_IMAGE', value: oldImage }, { name: 'DOCS_PORT', value: '8080' }];

async function fixture(t, options = {}) {
    const updates = [];
    const failures = [];
    const log = [];
    const states = [...(options.before ?? [1])];
    const after = [...(options.after ?? [3, 1])];
    const health = [...(options.health ?? ['starting', 'healthy'])];
    let accepted = false;
    let env = structuredClone(originalEnv);
    let file = options.file ?? compose;
    let containerChecks = 0;
    let lastHealth;

    function next(queue) {
        return queue.length > 1 ? queue.shift() : queue[0];
    }

    const server = createServer(async (request, response) => {
        try {
            assert.equal(request.headers['x-api-key'], 'fixture-key');
            const url = new URL(request.url, 'http://localhost');
            assert.ok(url.pathname.startsWith('/portainer/api/'), 'Preserve a reverse-proxy base path');
            const path = url.pathname.slice('/portainer/api/'.length);
            let body;
            let status = 200;
            if (options.httpError) {
                status = options.httpError;
                body = { message: 'Request refused' };
            } else if (request.method === 'GET' && path === 'stacks/7') {
                body = { Id: 7, Type: options.type ?? 2, EndpointId: 3, Name: 'glue-docs',
                    Status: next(accepted ? after : states), Env: env,
                    DeploymentStatus: [{ Message: 'Image pull failed' }], ...options.stack };
            } else if (request.method === 'GET' && path === 'stacks/7/file') {
                body = { StackFileContent: file };
            } else if (request.method === 'PUT' && path === 'stacks/7') {
                assert.equal(url.searchParams.get('endpointId'), '3');
                const chunks = [];
                for await (const chunk of request) chunks.push(chunk);
                const payload = JSON.parse(Buffer.concat(chunks).toString());
                updates.push(payload);
                if (options.conflict && updates.length === 1) {
                    status = 409;
                    body = {};
                    // A concurrent operator changed both settings while deploying.
                    env = [{ name: 'DOCS_PORT', value: '9090' }];
                    file = `${compose}    restart: unless-stopped\n`;
                    states.splice(0, states.length, 3, 1);
                } else {
                    accepted = true;
                    env = payload.Env;
                    body = { Status: 3 };
                }
            } else if (request.method === 'GET' && path === 'endpoints/3/docker/containers/json') {
                assert.equal(accepted, true);
                assert.deepEqual(JSON.parse(url.searchParams.get('filters')), { label: [
                    'com.docker.compose.project=glue-docs', 'com.docker.compose.service=docs',
                ] });
                containerChecks++;
                lastHealth = next(health);
                body = [{ Id: 'old' }, { Id: 'new' }];
            } else if (request.method === 'GET' && /^endpoints\/3\/docker\/containers\/(old|new)\/json$/.test(path)) {
                const old = path.includes('/old/');
                body = { Config: { Image: old || options.staleImage ? oldImage : image },
                    State: { Running: true, Status: 'running', Health: { Status: old ? 'healthy' : lastHealth } } };
            } else {
                throw new Error(`Unexpected request: ${request.method} ${request.url}`);
            }
            response.writeHead(status, { 'Content-Type': 'application/json' });
            response.end(JSON.stringify(body));
        } catch (error) {
            failures.push(error);
            response.writeHead(500, { 'Content-Type': 'application/json' });
            response.end('{}');
        }
    });
    server.listen(0, '127.0.0.1');
    await once(server, 'listening');
    t.after(async () => {
        server.closeAllConnections();
        await new Promise(resolve => server.close(resolve));
        assert.deepEqual(failures, []);
    });
    return {
        updates,
        log,
        containerChecks: () => containerChecks,
        run: overrides => deployDocumentation({
            url: `http://127.0.0.1:${server.address().port}/portainer/`,
            apiKey: 'fixture-key', stackId: '7', image,
            pollIntervalMs: 5, timeoutMs: 2_000, log: message => log.push(message), ...overrides,
        }),
    };
}

test('preserves the stack and settings, waits for async completion and the correct healthy image', async t => {
    const api = await fixture(t, { before: [3, 1] });
    await api.run();
    assert.deepEqual(api.updates, [{
        StackFileContent: compose,
        Env: [{ name: 'DOCS_PORT', value: '8080' }, { name: 'DOCS_IMAGE', value: image }],
        RepullImageAndRedeploy: true,
        Prune: false,
    }]);
    assert.equal(api.containerChecks(), 2);
    assert.equal(api.log.length, 1);
    assert.match(api.log[0], /healthy/);
});

test('a concurrent update is awaited and its latest configuration is preserved on retry', async t => {
    const api = await fixture(t, { conflict: true });
    await api.run();
    assert.equal(api.updates.length, 2);
    assert.match(api.updates[1].StackFileContent, /restart: unless-stopped/);
    assert.deepEqual(api.updates[1].Env, [{ name: 'DOCS_PORT', value: '9090' }, { name: 'DOCS_IMAGE', value: image }]);
});

for (const status of [2, 4]) {
    test(`an accepted update ending in status ${status} fails rather than reporting success`, async t => {
        const api = await fixture(t, { after: [3, status] });
        await assert.rejects(api.run(), new RegExp(`ended with status ${status}`));
        assert.equal(api.containerChecks(), 0);
        assert.equal(api.log.length, 0);
    });
}

for (const options of [{ type: 1 }, { stack: { GitConfig: { URL: 'https://example.org/repo.git' } } }, { stack: { WorkflowID: 12 } }]) {
    test(`refuses an incompatible stack before writing: ${JSON.stringify(options)}`, async t => {
        const api = await fixture(t, options);
        await assert.rejects(api.run(), /Compose stack|web editor/);
        assert.equal(api.updates.length, 0);
    });
}

test('refuses a stack without the managed image variable', async t => {
    const api = await fixture(t, { file: 'services:\n  unrelated:\n    image: nginx:alpine\n' });
    await assert.rejects(api.run(), /must reference/);
    assert.equal(api.updates.length, 0);
});

test('stops polling a permanently deploying stack within the configured timeout', async t => {
    const api = await fixture(t, { before: [3] });
    await assert.rejects(api.run({ timeoutMs: 100 }), /timed out|timeout/i);
    assert.equal(api.updates.length, 0);
});

test('an old healthy container cannot make a deployment of a different image succeed', async t => {
    const api = await fixture(t, { staleImage: true });
    await assert.rejects(api.run({ timeoutMs: 100 }), /timed out|timeout/i);
    assert.equal(api.log.length, 0);
});

test('an unhealthy new container fails the deployment', async t => {
    const api = await fixture(t, { health: ['unhealthy'] });
    await assert.rejects(api.run(), /failed its health check/);
    assert.equal(api.log.length, 0);
});

test('authentication failures stop immediately', async t => {
    const api = await fixture(t, { httpError: 403 });
    await assert.rejects(api.run(), /HTTP 403/);
    assert.equal(api.updates.length, 0);
});

test('requires an immutable image digest before contacting Portainer', async t => {
    const api = await fixture(t);
    await assert.rejects(api.run({ image: 'registry.example/glue/docs:latest' }), /pinned to its sha256 digest/);
    assert.equal(api.updates.length, 0);
});
