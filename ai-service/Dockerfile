FROM python:3.12-slim
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
WORKDIR /app
# requirements.txt is the Docker dependency source until a tested uv.lock is committed.
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt
COPY app ./app
COPY alembic ./alembic
COPY alembic.ini ./alembic.ini
RUN useradd --system --uid 10001 app
USER app
EXPOSE 8085
CMD ["uvicorn","app.main:app","--host","0.0.0.0","--port","8085"]
