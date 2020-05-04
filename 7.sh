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
}

trap "clean" 2

make

sudo docker build -t idserver .

sudo docker run -d --name server0 idserver
sleep 1
sudo docker run -d --name server1 idserver -i 172.17.0.2
sleep 1
sudo docker run -d --name server2 idserver -i 172.17.0.2
sleep 1

clean
