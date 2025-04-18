FROM python:3.12-slim-bullseye AS builder

WORKDIR /app

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1

RUN apt-get update && \
    apt-get install -y --no-install-recommends gcc

COPY requirements.txt ./
RUN pip wheel --no-cache-dir --no-deps --wheel-dir /app/wheels -r requirements.txt

RUN opentelemetry-bootstrap -a install

FROM python:3.12-slim-bullseye AS runtime

WORKDIR /app

COPY --from=builder /app/wheels /wheels
COPY --from=builder /app/requirements.txt .

RUN pip install --upgrade pip
RUN pip install --no-cache /wheels/*

COPY . .

ENV OTEL_PYTHON_LOGGING_AUTO_INSTRUMENTATION_ENABLED=true

CMD [ "opentelemetry-instrument", "gunicorn", "--bind", "0.0.0.0:8000", "main:app" ]