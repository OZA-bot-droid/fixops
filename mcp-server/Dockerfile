FROM python:3.12-slim
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
WORKDIR /app
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt
COPY app ./app
RUN useradd --system --uid 10001 app
USER app
EXPOSE 8090
# Placeholder health only; replace command after MCP SDK implementation.
CMD ["uvicorn","app.health_app:app","--host","0.0.0.0","--port","8090"]
