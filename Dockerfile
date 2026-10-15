FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY . .
RUN javac ChatServer.java MobileBridgeServer.java
CMD sh -c "java ChatServer & java MobileBridgeServer"