FROM docker.io/library/python:3.13.7-slim
WORKDIR /app
COPY traffic.py test_traffic.py ./
RUN python -m unittest -v && useradd --uid 10001 --no-create-home --shell /usr/sbin/nologin app
USER app
ENV PYTHONUNBUFFERED=1 PYTHONDONTWRITEBYTECODE=1
CMD ["python", "traffic.py"]
