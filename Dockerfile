FROM openjdk:11-jre-slim

ADD / /
WORKDIR /
#CMD ["sh", "-c", "./run-server.sh"]
ENTRYPOINT ["java", "-cp", "./src/:./inc/commons-cli-1.4/commons-cli-1.4.jar", "-Djava.security.policy=mysecurity.policy", "IdServer","-n", "5654", "--verbose"]
