# Glue Documentation

The Glue documentation is a VitePress project. Published Markdown lives in `src/content/docs/`.

- `development/` contains contributor guidance and is not part of the public site.
- Retired APIs and historical investigations remain in Git history, not alongside current guides.

## Requirements

- Node.js 22.12 or newer.
- pnpm 11.5.2 or newer.

## Commands

```shell
pnpm install --frozen-lockfile
pnpm dev
pnpm check
pnpm test:deploy
pnpm build
```

Run these commands from `docs/`. The production site is written to `docs/dist/`.

`build` also checks the generated HTML's local links, heading anchors and assets. It verifies that
every page declares a favicon and that the published PNG matches the library icon byte for byte.
`dev` and `build` copy `glue-core/src/main/resources/assets/glue/icon.png` to the generated
`docs/public/icon.png`; do not edit that generated file. Vite's public directory is explicitly set to
`docs/public/` because Markdown uses a separate `srcDir`.

The workshop is a tutorial project created by the reader; `glue-showcase` contains the runnable
integration examples. Keep their instructions separate. Public guides should describe completed
steps and supported APIs, not illustration requests or internal review notes.

## Container publication and deployment

The pipeline is defined in [`.gitlab/ci/docs.yml`](../.gitlab/ci/docs.yml):

All three documentation jobs run only in **Git tag pipelines**, alongside library releases. A branch
push does not trigger them, and releases rebuild the site even when no documentation file changed.

1. `docs-build` installs the locked pnpm dependencies, tests the deployment client, and builds VitePress.
2. `docs-image` waits for both `docs-build` and the Java `publish` job to succeed. It builds one image
   and pushes both `$CI_REGISTRY_IMAGE/docs:$CI_COMMIT_TAG` and `$CI_REGISTRY_IMAGE/docs:latest`, then
   publishes its exact digest as the `DOCS_IMAGE` dotenv artifact.
3. `docs-deploy` updates the configured Portainer Compose stack to that digest when the three
   Portainer variables below are configured. It preserves the current Compose file and all stack
   variables other than `DOCS_IMAGE`.

For example, Git tag `2.2.3` publishes `docs:2.2.3` and `docs:latest`. The Git tag is used verbatim,
not converted to a slug, and must be valid as a Docker image tag. `latest` follows the last successful
image publication; it does not select the highest semantic version. Portainer deploys the release's
digest so it does not depend on that moving alias.

The image job uses **`gcr.io/kaniko-project/executor:debug`** on the regular container runner, with
an empty entrypoint and no `shell` tag, Docker daemon or privileged-container requirement. This
matches the existing Kaniko pipelines and avoids Podman's additional namespace requirements
(`cannot clone: Operation not permitted` on restricted Docker runners).

Authentication uses `gitlab-ci-token` with GitLab's built-in **`CI_JOB_TOKEN`**, written to
`/kaniko/.docker/config.json` and removed when the job exits. This avoids depending on custom
overrides of `CI_REGISTRY_USER` or `CI_REGISTRY_PASSWORD`. The other documentation jobs use
`node:24-bookworm`. Nginx configuration is validated during the image build, and Kaniko's
`--digest-file` supplies the digest published to the registry. The image retains its Docker health check.

If registry login returns HTTP 401, verify that Container Registry is enabled and that the project
or group does not override `CI_REGISTRY`, `CI_REGISTRY_IMAGE` or `CI_JOB_TOKEN`. An absent
`docs-image.env` after a login failure is a consequence: that artifact is created only after a
successful push. Changing the container builder alone cannot correct rejected credentials.

The production image serves static files with Nginx on port **8080**; it contains no Node runtime.
It supports VitePress's extensionless URLs, real 404 responses, revalidated HTML and immutable hashed
assets. `/healthz` is the Docker health-check endpoint. The site is configured for the root of a
hostname, for example `https://docs.example.org/`, rather than a URL subdirectory.

## Create the Portainer stack

This flow targets **Portainer 2.45.1, Docker Standalone/Compose**, including Community Edition. It
uses the authenticated API, not a Business Edition stack webhook.

1. Push a release tag containing this CI configuration, matching `app.version` as required for the
   Java release, and let `docs-image` succeed. Until Portainer is configured, the images are published
   and deployment is skipped.
2. In Portainer, add the private GitLab container registry. A **Custom registry** entry with a GitLab
   deploy token scoped to `read_registry` is sufficient for pulling. The native GitLab integration
   instead requires a token with `read_api` and `read_registry`. Do not use a CI job token here:
   it expires after its job.
3. Create a dedicated stack, for example `glue-docs`, with the **Web editor** or **Upload**. Use
   [`deploy/compose.yml`](deploy/compose.yml). The deployment client deliberately refuses Git-managed
   stacks so it cannot detach their GitOps configuration.
4. Set these **Portainer stack environment variables**:

   | Variable | Value |
   |---|---|
   | `DOCS_IMAGE` | The full `registry/.../docs:<git-tag>` or `registry/.../docs:latest` reference, or the digest printed by `docs-image`. Subsequent CI deployments pin it to their release digest. |
   | `DOCS_PORT` | Optional host port, default `8080`. |

5. Deploy the stack. Serve it through your reverse proxy or visit `http://<docker-host>:<DOCS_PORT>`.
   You can adapt networks, ports and proxy labels in Portainer; the CI preserves the stored file.
   Keep the service name `docs`, the `${DOCS_IMAGE}` reference and its image health check, which are
   used to verify the deployed container.

There is no host bind mount or documentation volume: each image contains its complete site.

## Configure automatic updates

Add these variables under **GitLab → Settings → CI/CD → Variables**:

| Variable | Purpose |
|---|---|
| `PORTAINER_URL` | Portainer instance URL reachable from the deployment runner, such as `https://portainer.example.org`. Include a reverse-proxy prefix if used, but not `/api`. |
| `PORTAINER_API_KEY` | API key of a Portainer user allowed to manage this stack and access its Docker environment. Store it masked and protected. |
| `PORTAINER_STACK_ID` | Numeric ID of the stack created above, visible in its Portainer URL. |
| `PORTAINER_DEPLOY_TIMEOUT_SECONDS` | Optional total deployment timeout, default `600`. |

Protect the **release tags** (for example, a `2.*` tag pattern) so protected variables are available
in tag pipelines; protecting only the default branch is insufficient. The environment ID is read from
Portainer; no separate endpoint variable is needed. **Do not define `DOCS_IMAGE` in GitLab's variable
settings**: the image job supplies the digest through its artifact.

For Portainer using a private CA, configure GitLab's file-type `NODE_EXTRA_CA_CERTS` variable with
that CA certificate. TLS verification remains enabled.

After the variables are set, the next pushed release tag rebuilds the documentation, publishes its
versioned image plus `latest`, and updates the stack automatically after Java publication succeeds.
Branch pushes do not publish or deploy the documentation. No registry polling or manual pull is required.

The deployment job is serialized with `resource_group: docs-production`. Keep GitLab's **Prevent
outdated deployment jobs** setting enabled so an older pipeline cannot roll back a newer deployment.

Portainer 2.41+ accepts stack updates before deployment finishes. The client waits for `Status` to
leave Deploying, rejects Error/Inactive results, and checks through Portainer's Docker API that the
`docs` container uses the requested digest and is healthy. HTTP 200 alone is not treated as success.
An update already in progress is awaited; a conflicting update is retried once with fresh settings.

To identify the running image, the client inspects the requested registry digest on the stack's Docker
environment and compares the returned image `Id` with the container's `Image` field. Docker's image
ID and the registry manifest digest identify different objects. The container's `Config.Image` may
contain a tag, digest reference or image ID, so its text is not used to decide whether deployment
succeeded. A healthy container with a different image ID cannot satisfy the check.

The job logs each deployment phase and changes in the wait reason. A timeout includes the last
observed reason, including when an API response stalls:

- **Status Deploying**: Portainer is still applying the stack update.
- **Requested image not available**: Docker does not yet expose the pulled digest. Check Portainer's
  deployment logs and registry access if this persists.
- **No containers match**: check that the Compose service is named `docs` and that its containers have
  `com.docker.compose.service=docs` and `com.docker.compose.project=<Portainer stack name>` labels.
- **Expected image ID / observed IDs**: the service still runs a different image.
- **Health starting**: the expected image's container has not yet passed its health check.

Unchanged wait reasons are not repeated on every poll. An unhealthy or stopped container using the
expected image fails the job immediately; a container removed during inspection causes the list to
be refreshed. Docker image inspection retries HTTP 404 while waiting for the pull; permission and
server errors fail immediately.

## Local verification and rollback

From the repository root:

```shell
pnpm --dir docs install --frozen-lockfile
pnpm --dir docs test:deploy
pnpm --dir docs build
docker build -f docs/Dockerfile -t glue-docs:local docs
docker run --rm -p 8080:8080 glue-docs:local
```

Check `/`, `/getting-started`, `/web/`, `/healthz` and an unknown URL (which must return HTTP 404).
The deployment tests use a local HTTP fixture and do not contact a real Portainer instance.

To roll back, set the stack's `DOCS_IMAGE` to a previously published digest or release tag and update
the stack in Portainer. Registry retention must keep images needed for rollback. The next successful
automatic deployment will select its own newly published digest again.
