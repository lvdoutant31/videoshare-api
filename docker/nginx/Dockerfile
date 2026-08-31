FROM node:20-alpine AS frontend-build
WORKDIR /workspace
RUN corepack enable
COPY package.json pnpm-workspace.yaml .npmrc pnpm-lock.yaml ./
COPY packages/shared/package.json packages/shared/package.json
COPY yingliu-c/package.json yingliu-c/package.json
COPY yingliu-b/package.json yingliu-b/package.json
RUN pnpm install --frozen-lockfile
COPY packages packages
COPY yingliu-c yingliu-c
COPY yingliu-b yingliu-b
RUN pnpm build

FROM nginx:1.27-alpine
COPY docker/nginx/conf.d/default.conf /etc/nginx/conf.d/default.conf
COPY --from=frontend-build /workspace/yingliu-c/dist /usr/share/nginx/html/h5
COPY --from=frontend-build /workspace/yingliu-b/dist /usr/share/nginx/html/admin
