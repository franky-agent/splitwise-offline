# Dockerfile for the splitwise-offline relay — a minimal, storage-free WebSocket relay.
# Lets devices sync when they are NOT in proximity. The relay only forwards bytes,
# never stores app data. Run on any container host (Fly.io, Render, Railway, a VPS).
#
# Build:  docker build -t splitwise-relay .
# Run:    docker run -p 8787:8787 splitwise-relay
#         (or set PORT and -p accordingly)

FROM node:22-alpine

WORKDIR /app

# Copy manifest first for better layer caching.
COPY relay/package.json relay/package-lock.json ./
RUN npm ci --omit=dev

# Copy the relay server (only ~60 lines, no other source needed).
COPY relay/server.js ./

ENV PORT=8787
EXPOSE 8787

# Non-root user for a tiny bit of hardening.
USER node

CMD ["node", "server.js"]