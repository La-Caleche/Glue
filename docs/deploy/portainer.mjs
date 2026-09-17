import { setTimeout as delay } from 'node:timers/promises';
import { pathToFileURL } from 'node:url';

class PortainerError extends Error {
    constructor(method, path, status) {
        super(`Portainer ${method} ${path.split('?')[0]} returned HTTP ${status}`);
        this.status = status;
    }
}

/** Updates a file-based Compose stack, preserving its definition and unrelated environment values. */
export async function deployDocumentation({ url, apiKey, stackId, image,
    timeoutMs = 600_000, pollIntervalMs = 2_000, log = console.log }) {
    const base = new URL(url);
    if (!['https:', 'http:'].includes(base.protocol) || base.username || base.password || base.search || base.hash) {
        throw new Error('PORTAINER_URL must be the HTTP(S) instance URL without credentials, query or fragment');
    }
    base.pathname = `${base.pathname.replace(/\/+$/, '')}/`;
    const id = Number(stackId);
    if (!Number.isSafeInteger(id) || id < 1) throw new Error('PORTAINER_STACK_ID must be a positive integer');
    if (!apiKey?.trim()) throw new Error('PORTAINER_API_KEY is required');
    if (!/^[a-z0-9][a-z0-9._:/-]*@sha256:[a-f0-9]{64}$/.test(image ?? '')) {
        throw new Error('DOCS_IMAGE must be the registry image pinned to its sha256 digest');
    }
    if (!Number.isSafeInteger(timeoutMs) || timeoutMs <= 0 || !Number.isSafeInteger(pollIntervalMs) || pollIntervalMs <= 0) {
        throw new Error('Deployment timeout and polling interval must be positive');
    }
    const deadline = Date.now() + timeoutMs;
    const stackPath = `stacks/${id}`;
    let lastStatus;

    function progress(message) {
        if (message !== lastStatus) log(message);
        lastStatus = message;
    }

    function timeoutError() {
        return new Error(`Documentation deployment timed out: ${lastStatus}`);
    }

    function remaining() {
        const milliseconds = deadline - Date.now();
        if (milliseconds <= 0) throw timeoutError();
        return milliseconds;
    }

    async function pause() {
        await delay(Math.min(pollIntervalMs, remaining()));
    }

    async function request(method, path, body) {
        const budget = remaining();
        const signal = AbortSignal.timeout(Math.min(30_000, budget));
        try {
            const response = await fetch(new URL(`api/${path}`, base), {
                method,
                headers: { 'X-API-Key': apiKey, 'Content-Type': 'application/json' },
                body: body === undefined ? undefined : JSON.stringify(body),
                redirect: 'error',
                signal,
            });
            if (!response.ok) {
                await response.body?.cancel();
                throw new PortainerError(method, path, response.status);
            }
            return await response.json();
        } catch (error) {
            if (!signal.aborted) throw error;
            if (budget <= 30_000) throw timeoutError();
            throw new Error(`Portainer ${method} ${path.split('?')[0]} request timed out: ${lastStatus}`);
        }
    }

    async function idleStack() {
        while (true) {
            const stack = await request('GET', stackPath);
            if (stack.Id !== id || stack.Type !== 2 || !Number.isSafeInteger(stack.EndpointId) || stack.EndpointId < 1
                || typeof stack.Name !== 'string' || !stack.Name) {
                throw new Error('The configured Portainer stack must be a Docker Compose stack with an environment');
            }
            if (stack.GitConfig || stack.WorkflowID) {
                throw new Error('Create this stack with the Portainer web editor or file upload, not a Git repository');
            }
            if (stack.Status === 3) {
                progress(`Waiting for Portainer stack ${stack.Name}: status Deploying`);
                await pause();
                continue;
            }
            if (![1, 2, 4].includes(stack.Status)) throw new Error(`Unknown Portainer stack status: ${stack.Status}`);
            return stack;
        }
    }

    progress(`Reading Portainer stack ${id}`);
    // Another operator may start an update between our GET and PUT. Refresh the definition before retrying.
    for (let attempt = 0; attempt < 2; attempt++) {
        const stack = await idleStack();
        const file = await request('GET', `${stackPath}/file`);
        if (typeof file.StackFileContent !== 'string' || !file.StackFileContent.includes('${DOCS_IMAGE')) {
            throw new Error('The stack definition must reference ${DOCS_IMAGE} for the docs service');
        }
        if (!Array.isArray(stack.Env)) throw new Error('Portainer returned an invalid stack environment');
        const env = stack.Env.filter(entry => entry.name !== 'DOCS_IMAGE');
        env.push({ name: 'DOCS_IMAGE', value: image });
        try {
            progress(`Updating Portainer stack ${stack.Name} to ${image}`);
            await request('PUT', `${stackPath}?endpointId=${stack.EndpointId}`, {
                StackFileContent: file.StackFileContent,
                Env: env,
                RepullImageAndRedeploy: true,
                Prune: false,
            });
            progress(`Portainer accepted the update for stack ${stack.Name}; waiting for deployment completion`);
            break;
        } catch (error) {
            if (!(error instanceof PortainerError) || error.status !== 409 || attempt === 1) throw error;
            progress(`Portainer stack ${stack.Name} has a concurrent update; waiting before refreshing its settings`);
            await pause();
        }
    }

    // Portainer 2.41+ returns HTTP 200 before the background deployment completes.
    const deployed = await idleStack();
    if (deployed.Status !== 1) {
        const reason = deployed.DeploymentStatus?.at(-1)?.Message;
        throw new Error(`Portainer deployment ended with status ${deployed.Status}${reason ? `: ${reason}` : ''}`);
    }
    if (deployed.Env?.find(entry => entry.name === 'DOCS_IMAGE')?.value !== image) {
        throw new Error('The stack image changed during deployment');
    }

    const filters = encodeURIComponent(JSON.stringify({ label: [
        `com.docker.compose.project=${deployed.Name}`, 'com.docker.compose.service=docs',
    ] }));
    const dockerPath = `endpoints/${deployed.EndpointId}/docker`;
    const waiting = `Waiting for docs in stack ${deployed.Name}`;
    let expectedImageId;
    progress(`Resolving ${image} on Docker environment ${deployed.EndpointId}`);
    while (true) {
        if (!expectedImageId) {
            let expectedImage;
            try {
                expectedImage = await request('GET', `${dockerPath}/images/${encodeURIComponent(image)}/json`);
            } catch (error) {
                if (!(error instanceof PortainerError) || error.status !== 404) throw error;
                progress(`${waiting}: requested image ${image} is not available on Docker environment ${deployed.EndpointId}`);
                await pause();
                continue;
            }
            if (!/^sha256:[a-f0-9]{64}$/.test(expectedImage.Id ?? '')) {
                throw new Error('Docker returned an invalid image ID for the requested documentation digest');
            }
            // A registry manifest digest is not the Docker image configuration ID stored in container.Image.
            expectedImageId = expectedImage.Id;
            progress(`Resolved ${image} to Docker image ID ${expectedImageId}; checking docs in stack ${deployed.Name}`);
        }
        const containers = await request('GET', `${dockerPath}/containers/json?all=true&filters=${filters}`);
        const inspected = [];
        let disappeared = false;
        for (const container of containers) {
            try {
                inspected.push(await request('GET', `${dockerPath}/containers/${encodeURIComponent(container.Id)}/json`));
            } catch (error) {
                if (!(error instanceof PortainerError) || error.status !== 404) throw error;
                disappeared = true;
            }
        }
        const current = inspected.filter(container => container.Image === expectedImageId);
        if (current.some(container => !container.State?.Health)) {
            throw new Error('The docs container must keep the documentation image health check enabled');
        }
        if (current.some(container => container.State.Health.Status === 'unhealthy'
            || ['exited', 'dead'].includes(container.State.Status))) {
            throw new Error('The new documentation container failed its health check or stopped');
        }
        if (!disappeared && current.length > 0
            && current.every(container => container.State.Running && container.State.Health.Status === 'healthy')) {
            log(`Documentation stack ${deployed.Name} now runs ${image} (healthy)`);
            return;
        }
        if (containers.length === 0) {
            progress(`${waiting}: no containers match the Compose project/service labels`);
        } else if (disappeared) {
            progress(`${waiting}: a container disappeared during inspection; refreshing the container list`);
        } else if (current.length === 0) {
            const observed = [...new Set(inspected.map(container => container.Image ?? 'unknown'))].sort().join(', ');
            progress(`${waiting}: expected image ID ${expectedImageId}; observed ${observed}`);
        } else {
            const states = current.map(container => `${container.Id.slice(0, 12)}: ${container.State.Status}, `
                + `running=${container.State.Running}, health=${container.State.Health.Status}`).sort().join('; ');
            progress(`${waiting}: ${states}`);
        }
        await pause();
    }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
    try {
        await deployDocumentation({
            url: process.env.PORTAINER_URL,
            apiKey: process.env.PORTAINER_API_KEY,
            stackId: process.env.PORTAINER_STACK_ID,
            image: process.env.DOCS_IMAGE,
            timeoutMs: Number(process.env.PORTAINER_DEPLOY_TIMEOUT_SECONDS ?? 600) * 1000,
        });
    } catch (error) {
        const key = process.env.PORTAINER_API_KEY;
        console.error(key ? error.message.replaceAll(key, '[redacted]') : error.message);
        process.exitCode = 1;
    }
}
