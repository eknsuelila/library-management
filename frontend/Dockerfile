# =====================================================================================================
# Frontend container image (multi-stage build).
#   Stage 1 uses Node to compile the Angular app into static HTML/CSS/JS files.
#   Stage 2 serves those files with nginx - Node isn't needed at runtime at all.
# =====================================================================================================

# ---------- Stage 1: compile the Angular app ----------
FROM node:18-alpine AS build
WORKDIR /app
# Copy the dependency manifests first so "npm ci" is cached until they change (see backend/Dockerfile).
COPY package.json package-lock.json ./
# npm ci installs EXACTLY what package-lock.json lists: reproducible builds.
RUN npm ci
COPY . .
RUN npx ng build --configuration production

# ---------- Stage 2: serve the static files ----------
FROM nginx:1.27-alpine
# Our server block replaces nginx's default site.
COPY nginx.conf /etc/nginx/conf.d/default.conf
# Angular 17's application builder writes the browser bundle to dist/<project>/browser.
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html
EXPOSE 80
