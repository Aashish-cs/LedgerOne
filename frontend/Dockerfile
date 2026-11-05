FROM node:24-alpine AS build
WORKDIR /app
ARG VITE_API_BASE_URL=/api
ARG VITE_QUEUED_ORDERS=false
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
ENV VITE_QUEUED_ORDERS=$VITE_QUEUED_ORDERS
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.29-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
