FROM node:22-alpine AS build
WORKDIR /app
ARG VITE_AUTH_MODE=oidc
ARG VITE_CASDOOR_ISSUER=http://localhost:8000
ARG VITE_CASDOOR_CLIENT_ID=ragshared0client00000001-org-risk-platform
ARG VITE_CASDOOR_ORGANIZATION=risk-platform
ARG VITE_CASDOOR_SCOPE="openid profile offline_access"
ENV VITE_AUTH_MODE=${VITE_AUTH_MODE} \
    VITE_CASDOOR_ISSUER=${VITE_CASDOOR_ISSUER} \
    VITE_CASDOOR_CLIENT_ID=${VITE_CASDOOR_CLIENT_ID} \
    VITE_CASDOOR_ORGANIZATION=${VITE_CASDOOR_ORGANIZATION} \
    VITE_CASDOOR_SCOPE=${VITE_CASDOOR_SCOPE}
COPY risk-console/package.json risk-console/package-lock.json ./
RUN npm ci
COPY risk-console/ .
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.27-alpine
ENV RISK_ADMIN_UPSTREAM=http://risk-admin:8083 \
    CASDOOR_PUBLIC_URL=http://localhost:8000
COPY risk-console/nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
