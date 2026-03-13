# Build stage
FROM node:20-alpine AS builder
WORKDIR /app

COPY package*.json ./
RUN npm ci
COPY . .
ARG VITE_AUTH_SERVICE_URL=/auth-api
ENV VITE_AUTH_SERVICE_URL=$VITE_AUTH_SERVICE_URL
RUN npm run build

# Serve stage
FROM nginx:alpine
WORKDIR /usr/share/nginx/html

RUN rm -rf /usr/share/nginx/html/*
COPY nginx-default.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist/spa .

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
