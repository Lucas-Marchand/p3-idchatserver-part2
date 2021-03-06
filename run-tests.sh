#!/bin/bash
function clean(){
    echo ""
    echo "=====Server 0====="
    sudo docker logs server0

    echo ""
    echo "=====Server 1====="
    sudo docker logs server1

    echo ""
    echo "=====Server 2====="
    sudo docker logs server2

    echo ""
    echo "Killing Containers."
    sudo docker kill server0
    sudo docker kill server1
    sudo docker kill server2

    echo ""
    echo "Deleting Containers."
    sudo docker rm server0
    sudo docker rm server1
    sudo docker rm server2
    exit
}

trap "clean" 2

echo "=====Building code and docker image====="
echo ""
make clean
make
sudo docker build -t idserver .

echo ""
echo "=====Starting servers====="
echo ""
sudo docker run -d --name server0 idserver
sleep 1
sudo docker run -d --name server1 idserver -i 172.17.0.2
sleep 1
sudo docker run -d --name server2 idserver -i 172.17.0.2
sleep 1

echo ""
echo "=====Sending two concurrent requests to two different servers====="
echo ""
java -cp ./src/:./inc/commons-cli-1.4/commons-cli-1.4.jar IdClient -s 172.17.0.2 -c lucas -p marchand -n 5654 
java -cp ./src/:./inc/commons-cli-1.4/commons-cli-1.4.jar IdClient -s 172.17.0.3 -c Jim -p jeffrey -n 5654

sleep 10

echo ""
echo "=====Killing lead server====="
echo ""
sudo docker kill server0
sleep 1

echo ""
echo "=====Sending two concurrent requests(Election should happen)====="
echo ""
java -cp ./src/:./inc/commons-cli-1.4/commons-cli-1.4.jar IdClient -s 172.17.0.3 -g all -n 5654 
echo "First command finished"
java -cp ./src/:./inc/commons-cli-1.4/commons-cli-1.4.jar IdClient -s 172.17.0.2 172.17.0.5 -g all -n 5654
echo "Second finished. Sending a bunch of commands to see if it works"
java -cp ./src/:./inc/commons-cli-1.4/commons-cli-1.4.jar IdClient -s 172.17.0.3 -g all -n 5654
java -cp ./src/:./inc/commons-cli-1.4/commons-cli-1.4.jar IdClient -s 172.17.0.3 -g uuids -n 5654
java -cp ./src/:./inc/commons-cli-1.4/commons-cli-1.4.jar IdClient -s 172.17.0.3 -g users -n 5654
java -cp ./src/:./inc/commons-cli-1.4/commons-cli-1.4.jar IdClient -s 172.17.0.3 -m lucas john -p marchand -n 5654
java -cp ./src/:./inc/commons-cli-1.4/commons-cli-1.4.jar IdClient -s 172.17.0.3 -g all -n 5654
java -cp ./src/:./inc/commons-cli-1.4/commons-cli-1.4.jar IdClient -s 172.17.0.3 -d john -p marchand -n 5654
java -cp ./src/:./inc/commons-cli-1.4/commons-cli-1.4.jar IdClient -s 172.17.0.3 -g all -n 5654

sleep 5

clean
