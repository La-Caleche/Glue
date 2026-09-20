import assert from 'node:assert/strict';
import { execFileSync, spawnSync } from 'node:child_process';
import { createHash, generateKeyPairSync, verify } from 'node:crypto';
import { mkdtemp, mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import test from 'node:test';

const script = fileURLToPath(new URL('./publish-bundle.mjs', import.meta.url));

async function fixture(t) {
    const directory = await mkdtemp(join(tmpdir(), 'glue-bundle-publisher-'));
    t.after(() => rm(directory, { recursive: true, force: true }));
    const { privateKey, publicKey } = generateKeyPairSync('ed25519');
    await writeFile(join(directory, 'key.pem'), privateKey.export({ type: 'pkcs8', format: 'pem' }));
    const frontend = join(directory, 'frontend');
    await mkdir(frontend);
    await writeFile(join(frontend, 'index.html'), '<h1>Signed release</h1>');
    execFileSync('jar', ['--create', '--file', join(directory, 'bundle.zip'), '--no-manifest', '-C', frontend, '.']);
    return {
        directory, publicKey,
        run(extra = []) {
            return spawnSync(process.execPath, [script, '--archive', 'bundle.zip', '--app', 'demo:editor',
                '--contract', 'editor/1', '--version', '1.1.0', '--revision', '1',
                '--channel', 'https://updates.example/demo/stable.json', '--url', 'https://updates.example/demo/1.1.0.zip',
                '--key', 'key.pem', '--key-id', 'release', '--output', 'out', ...extra], { cwd: directory, encoding: 'utf8' });
        },
    };
}

test('publication produces a verifiable envelope bound to the actual ZIP bytes', async t => {
    const api = await fixture(t);
    const result = api.run();
    assert.equal(result.status, 0, result.stderr);
    const envelope = JSON.parse(await readFile(join(api.directory, 'out/channel.json'), 'utf8'));
    const payload = Buffer.from(envelope.payload, 'base64');
    assert.equal(verify(null, payload, api.publicKey, Buffer.from(envelope.signature, 'base64')), true);
    assert.deepEqual(payload, await readFile(join(api.directory, 'out/manifest.json')));
    const manifest = JSON.parse(payload);
    const archive = await readFile(join(api.directory, 'out/bundle.zip'));
    assert.deepEqual(archive, await readFile(join(api.directory, 'bundle.zip')));
    assert.equal(manifest.releases[0].size, archive.length);
    assert.equal(manifest.releases[0].sha256, createHash('sha256').update(archive).digest('hex'));
    assert.equal(manifest.app, 'demo:editor');
    assert.equal(envelope.keyId, 'release');
});

test('publishing another contract preserves old clients and requires a newer revision', async t => {
    const api = await fixture(t);
    assert.equal(api.run().status, 0);
    const result = api.run(['--contract', 'editor/2', '--revision', '2', '--catalog', 'out/manifest.json']);
    assert.equal(result.status, 0, result.stderr);
    const manifest = JSON.parse(await readFile(join(api.directory, 'out/manifest.json'), 'utf8'));
    assert.deepEqual(manifest.releases.map(release => release.contract), ['editor/1', 'editor/2']);
    assert.notEqual(api.run(['--revision', '2', '--catalog', 'out/manifest.json']).status, 0);
    assert.notEqual(api.run(['--app', 'other:editor', '--revision', '3', '--catalog', 'out/manifest.json']).status, 0);
});

test('the publication tool rejects insecure URLs and non-Ed25519 signing keys', async t => {
    const api = await fixture(t);
    assert.notEqual(api.run(['--url', 'http://updates.example/bundle.zip']).status, 0);
    const { privateKey } = generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
    await writeFile(join(api.directory, 'key.pem'), privateKey.export({ type: 'pkcs8', format: 'pem' }));
    const result = api.run();
    assert.notEqual(result.status, 0);
    assert.match(result.stderr, /Ed25519/);
});
