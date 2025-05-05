FROM python:3.12-slim-bullseye

WORKDIR /app

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1

RUN apt-get update && \
    apt-get install -y --no-install-recommends gcc

COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt
RUN opentelemetry-bootstrap -a install

COPY . .

# CMD [ "opentelemetry-instrument", "python", "main.py"]
ENV OTEL_SDK_DISABLED=true
CMD [ "opentelemetry-instrument", "flask", "--app", "main.py", "run", "--host=0.0.0.0"]
