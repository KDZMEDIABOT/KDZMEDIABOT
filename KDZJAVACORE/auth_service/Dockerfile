FROM node:20-alpine
WORKDIR /app

RUN addgroup app && adduser -G app -D app

# Copy package files first for better caching
COPY package.json package-lock.json* ./

# Force install dependencies and ensure dotenv is available
RUN npm install --omit=dev && \
    npm install dotenv && \
    npm cache clean --force

COPY server.js ./
#RUN chown -R app:app /app

#USER app

EXPOSE 3002
ENV PORT=3002
ENV NODE_ENV=production
CMD ["node", "server.js"]
