import java.time.LocalDateTime;
import java.net.InetAddress;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Timer;
import java.util.TimerTask;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.annotation.Inherited;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.time.Instant;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

/**
 * represents an IdServer containing a database of users
 */
public class IdServer extends UnicastRemoteObject implements Id {
	private static final long serialVersionUID = -5629717487800742372L;
	private static int registryPort = 1099;
	private static Map<String, User> lookupUsers = new HashMap<String, User>();
	private static Map<UUID, User> reverseLookupUsers = new HashMap<UUID, User>();
	private static boolean verbose = false;

    private static ArrayList<ServerInfo> serverList = new ArrayList<ServerInfo>();
    private static ServerInfo thisServer = null;
    private static ServerInfo leadServer = null;

    //If we are in an election, this should be true. Set it to false once a new leader has been selected.
    private static volatile boolean leaderIndeterm = false;


    private static synchronized boolean isLeader(){
        return leadServer.compareTo(thisServer) == 0;
    }

//=====Begin Election Methods=====

	/**
	 * Sends a victory message to all other servers
	 */
    private static void sendVictoryMessage(){
        System.out.println("[sendVictoryMessage]\t Sending victory message to all servers.");
        leadServer = thisServer;
        for(var server : serverList){
            if(thisServer.compareTo(server) == 0) continue;
            if(!serverAlive(server)) continue;

            try{
                ((Id)LocateRegistry.getRegistry(server.getAddress(), registryPort).lookup("server")).electionWon(thisServer);
            }catch(Exception e){}
        }
	}
	
	/**
	 * Sends an election message to all other servers
	 * @param server to send the election message to
	 * @return true if we sent message else false
	 */
    private static boolean sendElectionMessage(ServerInfo server){
        System.out.println("[sendElectionMessage]\t Sending election message to all higher servers.");
        try{
            ((Id)LocateRegistry.getRegistry(server.getAddress(), registryPort).lookup("server")).electionRequest(thisServer);
            return true;
        }catch(Exception e){}

        return false;
    }

	/**
	 * Runs an election between all servers
	 */
    private synchronized static void runElection(){
        System.out.println("[runElection]\t\t Election started at " + LocalDateTime.now());
        if(serverList.remove(leadServer)){
            System.out.println("Leader removed");
        }
        leaderIndeterm = true;
        //Check if we are the highest PID
        ServerInfo highestPID = Collections.max(serverList);
        if(thisServer.compareTo(highestPID) == 0){
            sendVictoryMessage();
            leaderIndeterm = false;
            return;
        }

        boolean leaderAlive = false;
        for(var server : serverList){
            if(thisServer.compareTo(server) < 0 && serverAlive(server)){   //Remote servere has greater pid
                if(sendElectionMessage(server)){
                    leaderAlive = true;
                }
            }
        }

        //No response, make self leader
        if(!leaderAlive){
            sendVictoryMessage();
            leaderIndeterm = false;
        }
    }

//===Begin Remote Election Methods===

	@Override
    public synchronized boolean electionRequest(ServerInfo sender){
        System.out.println("[electionRequest]\t\t Received election request from ip: " + sender.getAddress());
        if(thisServer.compareTo(sender) > 0){   //Sender has a lower PID (expected)
            runElection();
        }else{
            System.out.println("[electionRequest]\t\t Sender has a lower pid, ignoring. IP: " + sender.getAddress());
            return false;
        }

        return true;
    }

	@Override
    public synchronized void electionWon(ServerInfo newLeader){
        System.out.println("[electionWon]\t\t Election won at " + LocalDateTime.now());
        System.out.println("[electionWon]\t\t Election won by server with address " + newLeader.getAddress());
        leadServer = newLeader;
        leaderIndeterm = false;
    }

//===End Remote Election Methods===

//=====End Election Methods=====

	/**
	 * wrapper for an interrupt task
	 */
    private static class InterruptTask extends TimerTask{
        private Thread thd;
        public InterruptTask(Thread thread){
            thd = thread;
        }

        @Override
        public void run(){
            thd.interrupt();
        }
    }

    /**
	 * Checks if a particular server is alive
	 * @param server to check if it is able to accept sockets
	 * @return true if server is alive else false
	 */
    private static boolean serverAlive(ServerInfo server){
        System.out.println("[serverAlive]\t\t Received alive request at " + LocalDateTime.now());
        try{
            Socket socket = new Socket();
            socket.setSoTimeout(500);
            socket.connect(new InetSocketAddress(server.getAddress(), registryPort), 500);
            socket.close();
            System.out.println("[serverAlive]\t\t Verified server is alive at " + LocalDateTime.now());
            return true;
        }catch(Exception e){}
        System.out.println("[serverAlive]\t\t Server deemed dead at " + LocalDateTime.now());
        return false;
    }

	@Override
    public ServerInfo currentLeader(){
        System.err.println("[currentLeader]\t\t Received request for lead server");
        if(!serverAlive(leadServer)){
            System.err.println("[currentLeader]\t\t LeadServer not alive. Running Election.");
            runElection();
            //Wait for election to finish.
            //yes this is slow but we are running low on time
            while(leaderIndeterm){
                System.out.print(".");
            }
            System.out.println();

            System.out.println("[currentLeader]\t\t Election finished.");
            System.out.println("[currentLeader]\t\t New leader ip: " + leadServer.getAddress() + " PID: " + leadServer.getPID());
        }else{
            System.err.println("[currentLeader]\t\t Leader is still alive. IP:" + leadServer.getAddress());
        }


        return leadServer;
    }

    @Override
    public boolean isAlive(){
        System.out.println("[isAlive]\t\t Liveness check received");
        return true;
    }

    @Override
    public synchronized ArrayList<ServerInfo> registerServer(String newServerAddress){
        var highestPID = Collections.max(serverList);
        ServerInfo newServer = new ServerInfo(highestPID.getPID()+1, newServerAddress);
        serverList.add(newServer);

        //Notify
        for(var server : serverList){
            if(thisServer.compareTo(server) == 0) continue;

            try{
                ((Id)LocateRegistry.getRegistry(server.getAddress(), registryPort).lookup("server")).addNewServer(newServer);
            }catch(Exception e){}
        }

        System.out.println("[registryPort]\t\t Adding new server with ip of " + newServer.getAddress() + " and PID " + newServer.getPID());
        return serverList;
    }

    @Override
    public synchronized void addNewServer(ServerInfo newServer){
        System.out.println("[addNewServer]\t\t Adding new server with ip of " + newServer.getAddress() + " and PID " + newServer.getPID());
        serverList.add(newServer);
    }


	/**
	 * Represents a server that contains a database of users with replication and redundency built in
	 * @throws RemoteException
	 */
	public IdServer() throws RemoteException {
		super();
	}

	/**
	 * creates the command line options for parsing
	 * 
	 * @return
	 */
	private static Options setupOptions() {
		// create the Options
		Options options = new Options();

		// one option with optional value
		options.addOption("v", "verbose", false, "verbose output enables");

		// one option with optional value
		Option portOption = new Option("n", "numport", true, "changes port to connect RMI server");
		portOption.setOptionalArg(true);
		options.addOption(portOption);

		// one option with optional value
		Option serverIPOption = new Option("i", "serverip", true, "a list of the ip addresses for servers");
		serverIPOption.isRequired();
		serverIPOption.setArgs(Option.UNLIMITED_VALUES);
		options.addOption(serverIPOption);

		return options;
	}

	@Override
	public synchronized boolean modify(String oldLoginName, String newLoginName, String password) {
		if (verbose) {
			System.out.println("IdServer: client wishes to modify " + oldLoginName + " to " + newLoginName);
		}

		User user = lookupUsers.get(oldLoginName);

		if (user != null) {
			if (user.getPassword().equals(password)) {
				lookupUsers.put(newLoginName, user);
				lookupUsers.remove(oldLoginName);
				user.setTimeLastModified(Instant.now());
				if (verbose) {
					System.out.println("IdServer: " + oldLoginName + " is now " + newLoginName);
				}
				return true;
			} else {
				if (verbose) {
					System.out.println("IdServer: Incorrect password for " + oldLoginName);
					System.out.println("oldPass: " + user.getPassword());
				}
			}
		} else {
			if (verbose) {
				System.out.println("IdServer: could not find oldLoginName: " + oldLoginName);
			}
		}
		return false;
	}

	@Override
	public synchronized boolean delete(String loginName, String password) {
		if (lookupUsers.containsKey(loginName)) {
			User user = lookupUsers.get(loginName);
			if (user.getPassword().equals(password)) {
				if (verbose) {
					System.out.println("IdServer: Deleted user " + loginName);
				}
				lookupUsers.remove(loginName);
				return true;
			}
		}
		return false;
	}

	@Override
	public synchronized UUID create(String loginName, String realName, String password) {
		if (lookupUsers.containsKey(loginName)) {
			if (verbose) {
				System.out.println("IdServer: user already exists cannot create: " + loginName);
			}
			return null;
		} else {
			if (verbose) {
				System.out.println("IdServer: Created user" + loginName);
			}
			UUID uuid = UUID.randomUUID();
			User user = new User(loginName, uuid, realName, password);
			IdServer.reverseLookupUsers.put(uuid, user);
			IdServer.lookupUsers.put(loginName, user);
			return uuid;
		}
	}

	@Override
	public synchronized String lookup(String loginName) {
		User user = lookupUsers.get(loginName);
		if (user != null) {
			if (verbose) {
				System.out.println("IdServer: was able to lookup, user exists {" + user.getLoginName() + "}");
			}
			return user.toString();
		} else {
			if (verbose) {
				System.out.println("IdServer: cannot lookup, user does not exist");
			}
			return "User does not exist!";
		}
	}

	@Override
	public synchronized String reverseLookup(UUID uuid) {
		User user = reverseLookupUsers.get(uuid);
		if (user != null) {
			if (verbose) {
				System.out.println("IdServer: user exists {" + user.getLoginName() + "}");
			}
			return user.toString().toString();
		} else {
			if (verbose) {
				System.out.println("IdServer: User does not exist");
			}
			return "User does not exist!";
		}
	}

	@Override
	public synchronized void persistData() throws RemoteException {
		if (verbose) {
			System.out.println("IdServer: Persisting data securely to disk");
		}

		try {

			// Convert Map to byte array
			File file = new File("lookupUsers.ser");
			FileOutputStream fos = new FileOutputStream(file);
			ObjectOutputStream oout = new ObjectOutputStream(fos);
			oout.writeObject(lookupUsers);
			oout.close();
			fos.close();

			// Convert Map to byte array
			file = new File("reverseLookupUsers.ser");
			fos = new FileOutputStream(file);
			oout = new ObjectOutputStream(fos);
			oout.writeObject(reverseLookupUsers);

			oout.close();
			fos.close();

		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	@Override
	public synchronized String get(String listToGet) {
		if (verbose) {
			System.out.println("IdServer: client has asked to get information about users");
			System.out.println("IdServer: Query is: " + listToGet);
		}

		if (listToGet.equals("users")) {
			return IdServer.lookupUsers.keySet().toString();
		} else if (listToGet.equals("uuids")) {
			return IdServer.reverseLookupUsers.keySet().toString();
		} else if (listToGet.equals("all")) {
			StringBuilder sb = new StringBuilder();
			Set<String> set = lookupUsers.keySet();

			for (String s : set) {
				sb.append(s + ": ");
				sb.append(IdServer.lookupUsers.get(s).getUUID());
				sb.append("\n");
			}
			return sb.toString();
		}
		if (verbose) {
			System.out.println("IdServer: Did not recognize get query:" + listToGet);
		}
		return "Did not recognize list to get";
	}

	/**
	 * Binds or locates registry for IdServer
	 * 
	 * @param leaderAddr the address fo the leader to bind registry with
	 */
	public void bind(String leaderAddr) {
		try {
			// we will always need to register our server with registry weather backup or leader
			Registry registry = null;
			try {
				registry = LocateRegistry.getRegistry(registryPort);
				System.out.println(registry.list());
			} catch (RemoteException e) {
				registry = LocateRegistry.createRegistry(registryPort);
			}

			// create the servers remote object
			Id server = null;
			try {
				server = (IdServer) UnicastRemoteObject.exportObject(this, 0);
			} catch (java.rmi.server.ExportException e) {
				UnicastRemoteObject.unexportObject(this, true);
				server = (Id) UnicastRemoteObject.exportObject(this, 0);
			}

            String selfAddress = null;
            try{
                selfAddress = InetAddress.getLocalHost().getHostAddress();
            }catch(Exception e){
                System.out.println("Failed to get own address: " + e.toString());
                throw new RuntimeException();
            }


            if(leaderAddr == null){
                //We are the first, so we are the leader
                System.out.println("Other server not specified, making self leader.");
                ServerInfo us = new ServerInfo(1, selfAddress);
                leadServer = us;
                thisServer = us;
                serverList.add(us);
            }else{
                try{
                    leadServer = ((Id)LocateRegistry.getRegistry(leaderAddr, registryPort).lookup("server")).currentLeader();
                    System.out.println("Got leader");
                    System.out.println("Leader PID: " + leadServer.getPID());
                    System.out.println("Leader address: " + leadServer.getAddress());

                    serverList = ((Id)LocateRegistry.getRegistry(leaderAddr, registryPort).lookup("server")).registerServer(selfAddress);
                    System.out.println("Successfully registered with other server");
                    //Find ourselves
                    for(var server_ : serverList){
                        if(server_.getAddress().equals(selfAddress)){
                            thisServer = server_;
                            break;
                        }
                    }
                }catch(Exception e){
                    System.out.println("Unable to connect to provided server, aborting.");
                    System.out.println("IP: " + leaderAddr);
                    System.out.println(e.toString());
                    return;
                }
            }

            registry.rebind("server", server);
            System.out.println("server" + " bound in registry to port: " + registryPort);

		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("INFO: Exception occurred: " + e);
		}
	}

	/**
	 * represents all information about a user.
	 */
	private static void reloadDatabase() {
		try {
			File f = new File("lookupUsers.ser");
			File f2 = new File("reverseLookupUsers.ser");
			if (f.isFile() && f2.isFile()) {
				System.out.println("reloading database form disk");
				FileInputStream fis = new FileInputStream("lookupUsers.ser");
				ObjectInputStream ois = new ObjectInputStream(fis);
				lookupUsers = (Map<String, User>) ois.readObject();
				ois.close();
				fis.close();

				fis = new FileInputStream("reverseLookupUsers.ser");
				ois = new ObjectInputStream(fis);
				reverseLookupUsers = (Map<UUID, User>) ois.readObject();
				ois.close();
				fis.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Main entry of program
	 * 
	 * @param args
	 */
	public static void main(String args[]) {
        String selfAddress = null;
        try{
            System.out.println("[main]\t\t IP Address: "+ InetAddress.getLocalHost().getHostAddress());
            System.out.println("[main]\t\t Hostname  : "+ InetAddress.getLocalHost().getHostName());
            selfAddress = InetAddress.getLocalHost().getHostAddress();
        }catch(Exception e){
            System.out.println("Unable to get local address, aborting");
            return;
        }

		Options options = setupOptions();

		reloadDatabase();

		if (args.length < 1) {
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("java IdServer", options, true);
			System.exit(1);
		}

		try {
			// create the command line parser
			CommandLineParser parser = new DefaultParser();
			// parse the command line arguments
			CommandLine line = parser.parse(options, args);

            String leaderAddr = null;

			if (line.hasOption('i')) {
				leaderAddr = line.getOptionValue('i');
            }

			if (line.hasOption('n')) {
				registryPort = Integer.parseInt(line.getOptionValue('n'));
			}

			if (line.hasOption('v')) {
				verbose = true;
			}

			try {
				System.out.println("Setting System Properties....");
				IdServer server = new IdServer();
				server.bind(leaderAddr);

				// we need a timer running to make sure if we are a leader we persist data to disk
				int delay = 5000; // delay for 5 sec.
				int period = 5000; // repeat every 15 sec.
				Timer timer = new Timer();

				timer.scheduleAtFixedRate(new TimerTask() {
					public void run() {
						if (isLeader()){
							try {
								Registry registry = LocateRegistry.getRegistry("localhost", registryPort);
								Id stub = (Id) registry.lookup("server");
								stub.persistData();
							} catch (RemoteException e) {
								e.printStackTrace();
							} catch (NotBoundException e) {
								e.printStackTrace();
							}
						}
					}
				}, delay, period);
				
				// we need another timer that will get data from the leader every so often just in case we need to become a leader
				delay = 100; // delay for 100ms.
				period = 300; // repeat every 300 ms.
				Timer timer2 = new Timer();

				timer2.scheduleAtFixedRate(new TimerTask() {
					public void run() {
						try {
                            if(thisServer == leadServer) return;
                            if(!serverAlive(leadServer)) return;

							Registry registry = LocateRegistry.getRegistry(leadServer.getAddress(), registryPort);
							Id stub = (Id) registry.lookup("server");
							lookupUsers = stub.getLookupUsersDatabase();
							reverseLookupUsers = stub.getReverseLookupUsersDatabase();
						} catch (RemoteException e) {
							e.printStackTrace();
						} catch (NotBoundException e) {
							e.printStackTrace();
						}
					}
				}, delay, period);

				// Allow all server to persist data before being shut down
				Runtime.getRuntime().addShutdownHook(new Thread() {
					public void run() {
						try {
							System.out.println("Shutting down ...");
							Registry registry = LocateRegistry.getRegistry("localhost", registryPort);
							Id stub = null;
							if (isLeader()){
								stub = (Id) registry.lookup("server");
							}else {
								stub = (Id) registry.lookup("server");
							}
							stub.persistData();
						} catch (RemoteException e) {
							e.printStackTrace();
						} catch (NotBoundException e) {
							e.printStackTrace();
						}
					}
				});
			} catch (Exception e) {
				System.out.println("IdServer: " + e.getMessage());
				e.printStackTrace();
			}
		} catch (Exception e) {
			System.err.println("Client exception: " + e.toString());
			e.printStackTrace();
		}
	}

	@Override
	public Map<String, User> getLookupUsersDatabase() throws RemoteException {
		return new HashMap<String, User>(lookupUsers);
	}

	@Override
	public Map<UUID, User> getReverseLookupUsersDatabase() throws RemoteException {
		return new HashMap<UUID, User>(reverseLookupUsers);
	}
}
