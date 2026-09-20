import { createHash, createPrivateKey, createPublicKey, sign } from 'node:crypto';
import { copyFile, mkdir, readFile, stat, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { parseArgs } from 'node:util';

// This offline publication tool belongs to the producer's CI. Library builds never invoke Node.
const names = ['archive', 'app', 'contract', 'version', 'channel', 'revision', 'url', 'key', 'key-id', 'output', 'catalog'];
const { values } = parseArgs({ options: Object.fromEntries(names.map(name => [name, { type: 'string' }])) });
for (const name of names.filter(name => name !== 'catalog')) {
    if (!values[name]) throw new Error(`Missing --${name}`);
}
if (!/^[a-z0-9_-]+:[a-z0-9_-]+$/.test(values.app)) throw new Error('Expected --app mod-id:application-name');
if (!/^[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}$/.test(values.contract)) throw new Error('Invalid --contract');
if (!/^[A-Za-z0-9._-]{1,64}$/.test(values['key-id'])) throw new Error('Invalid --key-id');
if (!/^\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?(?:\+[0-9A-Za-z.-]+)?$/.test(values.version)) throw new Error('Expected a semantic --version');
for (const name of ['channel', 'url']) {
    const url = new URL(values[name]);
    if (url.protocol !== 'https:' || url.username || url.password || url.hash) throw new Error(`--${name} must use HTTPS without credentials or fragment`);
}
const revision = Number(values.revision);
if (!Number.isSafeInteger(revision) || revision < 1) throw new Error('--revision must be a positive safe integer');
const size = (await stat(values.archive)).size;
if (size < 1 || size > 64 * 1024 * 1024) throw new Error('Archive must be between 1 byte and 64 MiB');
const archive = await readFile(values.archive);
const sha256 = createHash('sha256').update(archive).digest('hex');
const previous = values.catalog ? JSON.parse(await readFile(values.catalog, 'utf8')) : null;
if (previous && (previous.format !== 1 || previous.app !== values.app || previous.channel !== values.channel
    || previous.revision >= revision || !Array.isArray(previous.releases))) {
    throw new Error('--catalog must be an earlier unsigned manifest for this application and channel');
}
const releases = (previous?.releases ?? []).filter(release => release.contract !== values.contract);
releases.push({ version: values.version, contract: values.contract, url: values.url, size: archive.length, sha256 });
const manifest = { format: 1, app: values.app, channel: values.channel, revision, releases };
const payload = Buffer.from(JSON.stringify(manifest));
const key = createPrivateKey(await readFile(values.key));
if (key.asymmetricKeyType !== 'ed25519') throw new Error('--key must be an Ed25519 private PEM key');
const envelope = JSON.stringify({
    format: 1, keyId: values['key-id'], payload: payload.toString('base64'),
    signature: sign(null, payload, key).toString('base64'),
});
if (Buffer.byteLength(envelope) > 256 * 1024) throw new Error('Signed channel exceeds 256 KiB');
await mkdir(values.output, { recursive: true });
const bundlePath = resolve(values.output, 'bundle.zip');
if (resolve(values.archive) !== bundlePath) await copyFile(values.archive, bundlePath);
await writeFile(resolve(values.output, 'manifest.json'), payload);
await writeFile(resolve(values.output, 'channel.json'), envelope);
console.log(`Upload bundle.zip to ${values.url}, then channel.json to ${values.channel}`);
console.log(`Archive SHA-256: ${sha256}`);
console.log(`Public key (SPKI Base64): ${createPublicKey(key).export({ type: 'spki', format: 'der' }).toString('base64')}`);
