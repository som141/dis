# syntax=docker/dockerfile:1
FROM node:24-bookworm-slim AS dependencies
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=secret,id=proxy_ca \
    if [ -f /run/secrets/proxy_ca ]; then export NODE_EXTRA_CA_CERTS=/run/secrets/proxy_ca; fi; \
    npm ci --ignore-scripts --strict-ssl=true
COPY build.mjs ./
COPY src ./src
COPY vendor ./vendor
COPY public ./public
COPY docker ./docker
RUN npm run build && npm prune --omit=dev --ignore-scripts && chmod -R a+rX /app

FROM node:24-bookworm-slim
RUN --mount=type=secret,id=proxy_ca \
    apt-get -o Acquire::https::CaInfo=/run/secrets/proxy_ca update && \
    apt-get -o Acquire::https::CaInfo=/run/secrets/proxy_ca install -y --no-install-recommends \
      chromium xvfb x11vnc novnc websockify openbox supervisor tini gosu \
      ca-certificates libnss3-tools fonts-noto-cjk x11-utils && \
    rm -rf /var/lib/apt/lists/*
WORKDIR /app
# One application layer avoids duplicating the large browser filesystem across
# many COPY steps on the managed environment's VFS storage driver.
COPY --from=dependencies /app /app
ENV DISPLAY=:99 HOME=/home/node NODE_USE_ENV_PROXY=1 \
    MAPLE_AUCTION_DATA_DIR=/home/node/maple-data MAPLE_BROWSER_PROFILE=/home/node/browser-profile
EXPOSE 7900
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=3 CMD ["node", "/app/docker/healthcheck.mjs"]
ENTRYPOINT ["/usr/bin/tini", "--", "/bin/sh", "/app/docker/entrypoint.sh"]
