FROM python:3.12-slim
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PATH="/app/.venv/bin:$PATH"
WORKDIR /app
# Pin uv to a tested release/digest before final deployment.
COPY --from=ghcr.io/astral-sh/uv:0.9.18 /uv /uvx /bin/
COPY pyproject.toml uv.lock ./
RUN uv sync --locked --no-dev --no-install-project
COPY app ./app
RUN useradd --system --uid 10001 app
USER app
EXPOSE 8090
# Initially exposes only diagnostic ping; no domain data tools are registered.
CMD ["python", "-m", "app.server"]
