FROM alpine

MAINTAINER Lucas Marchand <LucasMarchand158@u.boisestate.edu>
ADD ./* ./src/
WORKDIR /src/
RUN ls