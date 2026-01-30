FROM python:3.9-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY . .
# COPIA LA CHIAVE NEL CONTAINER
COPY key.json /app/key.json
# IMPOSTA LA VARIABILE D'AMBIENTE PER USARE LA CHIAVE
ENV GOOGLE_APPLICATION_CREDENTIALS="/app/key.json"
ENV PORT 8080
EXPOSE 8080
CMD ["sh", "-c", "gunicorn -w 2 -b :${PORT:-8080} --timeout 300 main:app"]
