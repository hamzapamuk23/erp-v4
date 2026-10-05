# Init scripts are baked into the image instead of bind-mounted: a bind-mount source resolves on the
# Docker daemon's filesystem, which on a remote daemon (CI docker:dind) does not contain this repo.
FROM postgres:18.6
COPY initdb/ /docker-entrypoint-initdb.d/
