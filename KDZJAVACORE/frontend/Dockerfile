# Build stage
FROM node:20-alpine AS builder
WORKDIR /app

COPY package*.json ./
RUN npm ci
COPY . .
ARG VITE_AUTH_SERVICE_URL=/auth-api
ARG SERVER_HOST=localhost
ENV VITE_AUTH_SERVICE_URL=$VITE_AUTH_SERVICE_URL
ENV SERVER_HOST=$SERVER_HOST
RUN npm run build

# Serve stage
FROM nginx:alpine
WORKDIR /usr/share/nginx/html

RUN rm -rf /usr/share/nginx/html/*
COPY nginx-default.conf /etc/nginx/conf.d/default.conf
COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh
COPY --from=builder /app/dist/spa .

EXPOSE 80
CMD ["/docker-entrypoint.sh"]
