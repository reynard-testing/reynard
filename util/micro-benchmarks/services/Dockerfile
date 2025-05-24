# Use official Node.js LTS image
FROM node:20-alpine

# Set working directory
WORKDIR /app

# Copy package files and install dependencies
COPY package*.json ./
RUN npm install

# Copy source code
COPY . .

# Build TypeScript code
RUN npm run build

# Start the application with OpenTelemetry auto-instrumentation
ARG SERVICE_NAME=leaf


WORKDIR /app/${SERVICE_NAME}

ENV OTEL_SERVICE_NAME=microservice
CMD ["node", "--require", "@opentelemetry/auto-instrumentations-node/register", "server.js"]