FROM openjdk:11-jre-slim

ADD / /
WORKDIR /
CMD ["sh", "-c", "./run-server.sh"]