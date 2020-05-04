import java.net.InetAddress;
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
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RemoteServer;
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

public class IdServer extends UnicastRemoteObject implements Id {
	private static final long serialVersionUID = -5629717487800742372L;
	private static int registryPort = 1099;
	private static Map<String, User> lookupUsers = new HashMap<String, User>();
	private static Map<UUID, User> reverseLookupUsers = new HashMap<UUID, User>();
	private static boolean verbose = false;
	private static String BACKUP_SERVER = "backupServer";
	private static String LEAD_SERVER = "leadServer";
	private static String LEADER_IP = "";
	private static String[] serverIPs = new String[0];
	private static Boolean isLeader = false;

    private static ArrayList<ServerInfo> serverList = new ArrayList<ServerInfo>();
    private static ServerInfo thisServer = null;
    private static ServerInfo leadServer = null;

    //If we are in an election, this should be true. Set it to false once a new leader has been selected.
    private static volatile boolean leaderIndeterm = false;


    private static synchronized boolean isLeader(){
        return leadServer.compareTo(thisServer) == 0;
    }

//=====Begin Election Methods=====

    private static void sendVictoryMessage(){
        System.out.println("[sendVictoryMessage]\t\t Sending victory message to all servers.");
        leadServer = thisServer;
        for(var server : serverList){
            if(thisServer.compareTo(server) == 0) continue;

            try{
                ((Id)LocateRegistry.getRegistry(server.address, registryPort).lookup("leadServer")).electionWon(thisServer);
            }catch(Exception e){}
        }
    }

    private static boolean sendElectionMessage(ServerInfo server){
        System.out.println("[sendElectionMessage]\t\t Sending election message to all higher servers.");
        try{
            ((Id)LocateRegistry.getRegistry(server.address, registryPort).lookup("leadServer")).electionRequest(thisServer);
            return true;
        }catch(Exception e){}

        return false;
    }

    private synchronized static void runElection(){
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
            if(thisServer.compareTo(server) < 0){   //Remote servere has greater pid
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
        System.out.println("[electionRequest]\t\t Received election request from ip: " + sender.address);
        if(thisServer.compareTo(sender) > 0){   //Sender has a lower PID (expected)
            runElection();
        }else{
            System.out.println("[electionRequest]\t\t Sender has a lower pid, ignoring. IP: " + sender.address);
            return false;
        }

        return true;
    }

    @Override
    public synchronized void electionWon(ServerInfo newLeader){
        System.out.println("[electionWon]\t\t Election won by server with address " + newLeader.address);
        leadServer = newLeader;
        leaderIndeterm = false;
    }

//===End Remote Election Methods===

//=====End Election Methods=====

    private boolean serverAlive(ServerInfo server){
        try{
            Registry registry = LocateRegistry.getRegistry(server.address, registryPort);
            Id stub = (Id) registry.lookup("leadServer");
            stub.isAlive();
        }catch(Exception e){
            return false;
        }
        return true;
    }

    @Override
    public synchronized ServerInfo currentLeader(){
        if(!serverAlive(leadServer)){
            System.out.println("[currentLeader]\t\t LeadServer not alive. Running Election.");
            runElection();
            //Wait for election to finish.
            //yes this is slow but we are running low on time
            while(leaderIndeterm){}

            System.out.println("[currentLeader]\t\t Election finished.");
            System.out.println("[currentLeader]\t\t New leader ip: " + leadServer.address + " PID: " + leadServer.pid);
        }

        return leadServer;
    }

    @Override
    public void isAlive(){
        System.out.println("[isAlive]\t\t Liveness check received");
    }

    @Override
    public synchronized ArrayList<ServerInfo> registerServer(String newServerAddress){
        var highestPID = Collections.max(serverList);
        ServerInfo newServer = new ServerInfo();
        newServer.address = newServerAddress;
        newServer.pid = highestPID.pid+1;
        serverList.add(newServer);

        //Notify
        for(var server : serverList){
            if(thisServer.compareTo(server) == 0) continue;

            try{
                ((Id)LocateRegistry.getRegistry(server.address, registryPort).lookup("leadServer")).addNewServer(newServer);
            }catch(Exception e){}
        }

        System.out.println("[registryPort]\t\t Adding new server with ip of " + newServer.address + " and PID " + newServer.pid);
        return serverList;
    }

    @Override
    public synchronized void addNewServer(ServerInfo newServer){
        System.out.println("[addNewServer]\t\t Adding new server with ip of " + newServer.address + " and PID " + newServer.pid);
        serverList.add(newServer);
    }


	/**
	 * @see
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

	/**
	 * @see
	 */
	@Override
	public synchronized boolean modify(String oldLoginName, String newLoginName, String password) {
		if (verbose) {
			System.out.println("IdServer: client wishes to modify " + oldLoginName + " to " + newLoginName);
		}

		User user = lookupUsers.get(oldLoginName);

		if (user != null) {
			if (user.password.equals(password)) {
				lookupUsers.put(newLoginName, user);
				lookupUsers.remove(oldLoginName);
				user.timeLastModified = Instant.now();
				if (verbose) {
					System.out.println("IdServer: " + oldLoginName + " is now " + newLoginName);
				}
				return true;
			} else {
				if (verbose) {
					System.out.println("IdServer: Incorrect password for" + oldLoginName);
				}
			}
		} else {
			if (verbose) {
				System.out.println("IdServer: could not find oldLoginName: " + oldLoginName);
			}
		}
		return false;
	}

	/**
	 * @see
	 */
	@Override
	public synchronized boolean delete(String loginName, String password) {
		if (lookupUsers.containsKey(loginName)) {
			User user = lookupUsers.get(loginName);
			if (user.password.equals(password)) {
				if (verbose) {
					System.out.println("IdServer: Deleted user " + loginName);
				}
				lookupUsers.remove(loginName);
				return true;
			}
		}
		return false;
	}

	/**
	 * @see
	 */
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

	/**
	 * @see
	 */
	@Override
	public synchronized String lookup(String loginName) {
		User user = lookupUsers.get(loginName);
		if (user != null) {
			if (verbose) {
				System.out.println("IdServer: was able to lookup, user exists {" + user.loginName + "}");
			}
			return user.toString();
		} else {
			if (verbose) {
				System.out.println("IdServer: cannot lookup, user does not exist");
			}
			return "User does not exist!";
		}
	}

	/**
	 * @see
	 */
	@Override
	public synchronized String reverseLookup(UUID uuid) {
		User user = reverseLookupUsers.get(uuid);
		if (user != null) {
			if (verbose) {
				System.out.println("IdServer: user exists {" + user.loginName + "}");
			}
			return user.toString().toString();
		} else {
			if (verbose) {
				System.out.println("IdServer: User does not exist");
			}
			return "User does not exist!";
		}
	}

	/**
	 * @see
	 */
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

	/**
	 * @see
	 */
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
				sb.append(IdServer.lookupUsers.get(s).uuid);
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
	 * @param name
	 */
	public void bind() {
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

			// determnine if we bind our name as a leader or a backup
			if (foundLeader()) {
                System.out.println("Leader found, starting as backup");
				isLeader = false;
				registry.rebind(BACKUP_SERVER, server);
				System.out.println(BACKUP_SERVER + " bound in registry to port: " + registryPort);
			} else {
                System.out.println("No leader found, making self leader");
				isLeader = true;
				registry.rebind(LEAD_SERVER, server);
				System.out.println(LEAD_SERVER + " bound in registry to port: " + registryPort);
			}

		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("INFO: Exception occurred: " + e);
		}
	}

	private boolean foundLeader() {
		for (String string : serverIPs) {
			try {
				Registry r = LocateRegistry.getRegistry(string, registryPort);
				r.lookup(LEAD_SERVER);
				LEADER_IP = string;
				System.out.println("Leader IP is: " + LEADER_IP);
				
				return true;
			} catch (RemoteException e) { // didnt find a registry
				continue;
			} catch (NotBoundException e) { // didnt find a bound name
				continue;
			}
		}
		return false;
	}

	/**
	 * represents all information about a user.
	 * 
	 * @author Lucas
	 *
	 */
	public class User {

		public UUID uuid;
		public String loginName;
		public String realName;
		public String password;
		public Instant timeCreated = Instant.now();
		public Instant timeLastModified = Instant.now();

		/**
		 * creates a new user with credentials
		 * 
		 * @param login
		 * @param userUUID
		 * @param real
		 * @param password2
		 */
		public User(String login, UUID userUUID, String real, String password2) {
			this.loginName = login;
			this.uuid = userUUID;
			this.realName = real;
			this.password = password2;
		}

		/**
		 * gives us a way to display user data
		 */
		@Override
		public String toString() {
			return "Login Name: " + loginName + "; Real Name: " + realName + "; UUID: " + uuid + "; Last Modified: "
					+ timeLastModified + "; Time Created: " + timeCreated;
		}
	}

	@SuppressWarnings("unchecked")
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
        try{
            System.out.println("[main]\t\t IP Address: "+ InetAddress.getLocalHost().getHostAddress());
            System.out.println("[main]\t\t Hostname  : "+ InetAddress.getLocalHost().getHostName());
        }catch(Exception e){}

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

			if (line.hasOption('i')) {
				serverIPs = line.getOptionValues('i');
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
				server.bind();

				// we need a timer running to make sure if we are a leader we persist data to disk
				int delay = 5000; // delay for 5 sec.
				int period = 30000; // repeat every 15 sec.
				Timer timer = new Timer();

				timer.scheduleAtFixedRate(new TimerTask() {
					public void run() {
						if (isLeader){
							try {
								Registry registry = LocateRegistry.getRegistry("localhost", registryPort);
								Id stub = (Id) registry.lookup(LEAD_SERVER);
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
				delay = 5000; // delay for 5 sec.
				period = 30000; // repeat every 15 sec.
				Timer timer2 = new Timer();

				timer2.scheduleAtFixedRate(new TimerTask() {
					public void run() {
						try {
							Registry registry = LocateRegistry.getRegistry(LEADER_IP, registryPort);
							Id stub = (Id) registry.lookup(LEAD_SERVER);
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
							if (isLeader){
								stub = (Id) registry.lookup(LEAD_SERVER);
							}else {
								stub = (Id) registry.lookup(BACKUP_SERVER);
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
