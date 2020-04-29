# Identity Server | Project 2
#### Lucas Marchand and Jared White
#### CS455-001 S2020
#### [Video Link](https://youtu.be/VIXfTY-yx0w)

## Table of contents
  - [Table of contents](#table-of-contents)
  - [File/Folder Manifest](#filefolder-manifest)
  - [Building and running](#building-and-running)
  - [Testing](#testing)
  - [Reflection](#reflection)

## File/Folder Manifest
```
p2-Identity-Server
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
│   └── User.java               Representation of a user
└── test.sh                     Runs some test actions (requires server to be running)
```

## Building and running

To build
```bash
make
```

Usage is same as project specs. Test scripts can also be used. To use the test scripts, start the run-server.sh. Leave it running, and then run the test.sh script to run some basic actions.
The run-client.sh scripts is a less-featured version of the test.sh script.

## Testing
We used the testing scripts (included with the code) to ensure that everything was working properly. We also tested it with a bunch of clients concurrently (multiple-clients.sh)

## Reflection
The roles were basically reversed compared to last project. Lucas wrote most of the code, and Jared did most of the architecture design. In the future, we will probably try to split both types of work between the two of us.
