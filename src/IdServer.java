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
		
		if(user != null) {
			if (user.password.equals(password)) {
				lookupUsers.put(newLoginName, user);
				lookupUsers.remove(oldLoginName);
				user.timeLastModified = Instant.now();
				if (verbose) {
					System.out.println("IdServer: " + oldLoginName + " is now " + newLoginName);
				}
				return true;
			}else {
				if (verbose) {
					System.out.println("IdServer: Incorrect password for" + oldLoginName);
				}
			}
		}else {
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
	public void bind(String name) {
		try {
			Registry registry = null;
			try {
				registry = LocateRegistry.getRegistry(registryPort);
				System.out.println(registry.list());
			} catch (RemoteException e) {
				registry = LocateRegistry.createRegistry(registryPort);
			}

			Id server = null;
			try {
				server = (IdServer) UnicastRemoteObject.exportObject(this, 0);
			} catch (java.rmi.server.ExportException e) {
				UnicastRemoteObject.unexportObject(this, true);
				server = (Id) UnicastRemoteObject.exportObject(this, 0);
			}

			registry.rebind(name, server);
			System.out.println(name + " bound in registry to port: " + registryPort);
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println(name + ": Exception occurred: " + e);
		}
	}

	/**
	 * Main entry of program
	 * 
	 * @param args
	 */
	public static void main(String args[]) {

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

			if (line.hasOption('n')) {
				registryPort = Integer.parseInt(line.getOptionValue('n'));
			}

			if (line.hasOption('v')) {
				verbose = true;
			}

			try {
				System.out.println("Setting System Properties....");
				IdServer server = new IdServer();
				server.bind("IdServer");

				int delay = 5000; // delay for 5 sec.
				int period = 30000; // repeat every 15 sec.
				Timer timer = new Timer();

				timer.scheduleAtFixedRate(new TimerTask() {
					public void run() {
						try {
							Registry registry = LocateRegistry.getRegistry("localhost", registryPort);
							Id stub = (Id) registry.lookup("IdServer");
							stub.persistData();
						} catch (RemoteException e) {
							e.printStackTrace();
						} catch (NotBoundException e) {
							e.printStackTrace();
						}
					}
				}, delay, period);

				// Allow server to persist data before it shut down
				Runtime.getRuntime().addShutdownHook(new Thread() {
					public void run() {
						try {
							System.out.println("Shutting down ...");
							Registry registry = LocateRegistry.getRegistry("localhost", registryPort);
							Id stub = (Id) registry.lookup("IdServer");
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
	 * represents all information about a user.
	 * 
	 * @author Lucas
	 *
	 */
	private class User {

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
}