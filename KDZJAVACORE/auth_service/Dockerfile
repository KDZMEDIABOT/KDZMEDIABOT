FROM node:20-alpine
WORKDIR /app

RUN addgroup app && adduser -G app -D app
COPY package*.json ./
RUN npm ci --omit=dev
COPY server.js ./
RUN chown -R app:app /app

USER app

EXPOSE 3001
ENV PORT=3001
CMD ["node", "server.js"]
