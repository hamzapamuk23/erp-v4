# Playwright runner for the compose smoke test. The image tag must match @playwright/test in web/pnpm-workspace.yaml.
FROM mcr.microsoft.com/playwright:v1.63.0-noble
WORKDIR /work/web
RUN corepack enable
COPY web/ ./
RUN pnpm install --frozen-lockfile --filter "@erp/e2e..."
WORKDIR /work/web/e2e
ENV CI=true
CMD ["pnpm", "exec", "playwright", "test"]
