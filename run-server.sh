#!/bin/bash
java -cp ./src/:./inc/commons-cli-1.4/commons-cli-1.4.jar -Djava.security.policy=mysecurity.policy IdServer -i 172.17.0.1 172.17.0.2 172.17.0.3 172.17.0.4 172.17.0.5 -n 5654 --verbose
