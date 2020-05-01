echo "INFO: building project"
make clean
make

echo "INFO: setting up docker"
sudo docker build -t idserver .
sudo docker run -d --name server0 idserver
sudo docker run -d --name server1 idserver
sleep 1

echo ""
echo "INFO: running client"
java -cp ./src/:./inc/commons-cli-1.4/commons-cli-1.4.jar IdClient 172.17.0.2 -c lucas -p marchand -n 5654
java -cp ./src/:./inc/commons-cli-1.4/commons-cli-1.4.jar IdClient 172.17.0.3 -c John -p johnson -n 5654
java -cp ./src/:./inc/commons-cli-1.4/commons-cli-1.4.jar IdClient 172.17.0.2 -g all -n 5654
java -cp ./src/:./inc/commons-cli-1.4/commons-cli-1.4.jar IdClient 172.17.0.3 -g all -n 5654

echo ""
echo "INFO: Stopping servers..."
sudo docker stop server0
sudo docker stop server1

echo ""
echo "INFO: Starting server again"
sudo docker start server0

sleep 5

java -cp ./src/:./inc/commons-cli-1.4/commons-cli-1.4.jar IdClient 172.17.0.2 -g all -n 5654
# java -cp ./src/:./inc/commons-cli-1.4/commons-cli-1.4.jar IdClient localhost -g uuids -n 5654
# java -cp ./src/:./inc/commons-cli-1.4/commons-cli-1.4.jar IdClient localhost -g users -n 5654
# java -cp ./src/:./inc/commons-cli-1.4/commons-cli-1.4.jar IdClient localhost -m lucas john -p marchand -n 5654
# java -cp ./src/:./inc/commons-cli-1.4/commons-cli-1.4.jar IdClient localhost -g all -n 5654
# java -cp ./src/:./inc/commons-cli-1.4/commons-cli-1.4.jar IdClient localhost -d john -p marchand -n 5654
# java -cp ./src/:./inc/commons-cli-1.4/commons-cli-1.4.jar IdClient localhost -g all -n 5654


echo ""
echo "INFO: Stopping servers..."
sudo docker stop server0
sudo docker stop server1

echo ""
echo "INFO: removing docker server containers"
sudo docker rm server0
sudo docker rm server1