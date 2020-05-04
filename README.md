# Identity Server Part 2 | Project 3
#### Lucas Marchand and Jared White
#### CS455-001 S2020
#### [Video Link]()

## Table of contents
  - [Table of contents](#table-of-contents)
  - [File/Folder Manifest](#filefolder-manifest)
  - [Building and running](#building-and-running)
  - [Testing](#testing)
  - [Reflection](#reflection)

## File/Folder Manifest
```
p3-idserver-part2
├── inc                         Dependencies
│   └── commons-cli-1.4         Command line arguments library
├── Makefile                    Makefile for project
├── multiple-clients.sh         Script to test with multiple clients
├── run-client.sh               Script to run client
├── run-server.sh               Script to run server
├── src                         Source directory
│   ├── IdClient.java           Client class
│   ├── Id.java                 RMI interface
│   ├── IdServer.java           Server class
│   ├── mysecurity.policy       Security policy for JVM
│   |── User.java               Representation of a user
│   └── ServerInfo.java         Representation of an IdServer
└── run-tests.sh                Script to test running multiple servers, elections, and replication of servers
```

## Building and running

To build
```bash
make
```

Utilize the run-server.sh and run-client.sh scripts to run the server and clients for the project. run-tests.sh is a great example of how
docker and the IdServer can be utilized for replication and consistency with clients. The differences between this phase of the IdServer and
the previous phase is that this phase has a lead server that handles communication with clients and that has backup servers that are ready to
run an election and step into the leaders place if the leader should fail to respond to clients. This is shown in the run-tests.sh script where
the docker container for the leader is killed. The election system is ran assigning the server with the highest PID as the winner. A PID in this
context is based on communication between servers on when they were created and is assigned and coordinated by the lead server when a new server
comes online and registers with the lead server.This model allows the IdServer to know has replicaiton and consistency. 

## Testing
We used the testing scripts (included with the code) to ensure that everything was working properly. We heavily utilized the run-tests.sh script
which setup docker containers for each server to show how they can be run in a distributive manner.

## Reflection
RMI for java is a very difficult way to handle distributed systems. There seems to be an overload of error handling that is hard to track and 
troubleshoot effectively. There wasn't as much documentation on RMI as I would have liked there to be a little more literature on the subject.
It seemed that RMI did not give good ways of handling timeouts with clients. We ended up moving to a socket based approach to check if a server
was alive instead of trying to get a reference to its registry.
