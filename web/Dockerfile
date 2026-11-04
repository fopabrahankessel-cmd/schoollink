# Production image of the web dashboard: the built React app served by Caddy, which also
# terminates HTTPS and forwards /api to the backend (docs/DEPLOYMENT.md, deploy/Caddyfile).
FROM node:22-alpine AS build
WORKDIR /web
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM caddy:2-alpine
COPY --from=build /web/dist /srv
