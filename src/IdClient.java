import java.net.Socket;
import java.net.InetSocketAddress;
import java.time.LocalDateTime;

import java.util.Optional;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

/**
 * Represents an RMI client for the IdServer
 * 
 * @author Lucas
 *
 */
public class IdClient {

	/**
	 * Try encoding with SHA 512
	 * 
	 * @param input
	 * @throws NoSuchAlgorithmException
	 */
	private static String trySHA(String input) throws NoSuchAlgorithmException {
		MessageDigest md = MessageDigest.getInstance("SHA-512");
		byte[] bytes = input.getBytes();
		md.reset();

		byte[] result = md.digest(bytes);
		return new String(result);
	}

	/**
	 * @see
	 * @param address
	 * @param port
	 * @return
	 */
    private static boolean serverAlive(String address, int port){
        System.out.println("[serverAlive]\t\t Received alive request at " + LocalDateTime.now());
        try{
            Socket socket = new Socket();
            socket.setSoTimeout(500);
            socket.connect(new InetSocketAddress(address, port), 500);
            socket.close();
            System.out.println("[serverAlive]\t\t Verified server is alive at " + LocalDateTime.now());
            return true;
        }catch(Exception e){}
        System.out.println("[serverAlive]\t\t Server deemed dead at " + LocalDateTime.now());
        return false;
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
		Option portOption = new Option("n", "numport", true, "changes port to connect to RMI server");
		portOption.setOptionalArg(true);
		options.addOption(portOption);

		// one option with optional value
		Option realNameOption = new Option("r", "real name", true, "allows the user to give their real name");
		portOption.setOptionalArg(true);
		options.addOption(realNameOption);

		// one option with optional value
		Option passwordOption = new Option("p", "password", true, "allows the user to give their real name");
		portOption.setOptionalArg(true);
		options.addOption(passwordOption);

		options.addOption("l", "lookup", true, "looks up a user by the given login name");
		options.addOption("r", "reverse-lookup", true, "looks up a user by the given UUID");

		// this option requires one value
		Option createOption = new Option("c", "create", true, "create an account with the given login name");
		options.addOption(createOption);

		// one way to create an option that requires multiple values
		Option modifyOption = new Option("m", "modify", true, "modify existing login name");
		modifyOption.setArgs(2);
		modifyOption.setArgName("oldLoginname> <newLoginname");
		options.addOption(modifyOption);

		options.addOption("d", "delete", true, "deletes an account with the given login name");

		Option getOption = new Option("g", "get", true, "retreives the given information form the server");
		getOption.setArgName("users|uuids|all");
		options.addOption(getOption);

		// one option with optional value
		Option serverIPOption = new Option("s", "servers", true, "a list of the ip addresses for servers");
		serverIPOption.isRequired();
		serverIPOption.setArgs(Option.UNLIMITED_VALUES);
		options.addOption(serverIPOption);

		return options;
	}

	/**
	 * Finds the leader of the IdServers
	 * @param serverIPs the list of server IP addresses to search
	 * @param registryPort the registry port that the leader would be bound to
	 * @return the ServerInfo of the lead server
	 */
	private static Optional<ServerInfo> findLeader(String[] serverIPs, int registryPort) {
		for (String string : serverIPs) {
			try {
                if(!serverAlive(string, registryPort)){
                    System.out.println("Server " + string + " did not respond to a ping, skipping.");
                    continue;
                }

                var result =  Optional.of(((Id)LocateRegistry.getRegistry(string, registryPort).lookup("server")).currentLeader());
                System.out.println("IP " + string + " is alive.");
                return result;
			} catch(Exception e){
                System.out.println("IP " + string + " failed, trying next one.");
            }
		}
        return Optional.empty();
	}

	/**
	 * Main entry point for the IdClient
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		Options options = setupOptions();

		if (args.length < 1) {
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("java IdClient", options, true);
			System.exit(1);
		}

		try {
			// create the command line parser
			CommandLineParser parser = new DefaultParser();
			// parse the command line arguments
			CommandLine line = parser.parse(options, args);
			int registryPort = 1099;

			if (line.hasOption('n')) {
				registryPort = Integer.parseInt(line.getOptionValue('n'));
			}

			String[] servers = line.getOptionValues('s');
			
			if (servers.length == 0) {
				System.out.println(options);
			}

			var host = findLeader(servers, registryPort);

			if (!host.isPresent()){
				System.out.println("no leader was found by client");
				System.exit(1);
			}

            System.out.println("Leader is " + host.get().getAddress());
			
			Registry registry = LocateRegistry.getRegistry(host.get().getAddress(), registryPort);
			Id stub = (Id) registry.lookup("server");

			// check if the user wants to lookup someone with login name
			if (line.hasOption('l')) {
				String lookup_name = line.getOptionValue('l');
				System.out.println(stub.lookup(lookup_name));
				System.exit(0);
			}

			// check if the user wants to lookup someone with uuid
			if (line.hasOption('r')) {
				UUID uuid = UUID.fromString(line.getOptionValue('r'));
				System.out.println(stub.reverseLookup(uuid));
				System.exit(0);
			}

			if (line.hasOption('g')) {
				String op = line.getOptionValue('g');
				if (op.equals("all") | op.equals("uuids") | op.equals("users")) {
					System.out.println(stub.get(op));
				}
				System.exit(0);
			}

			String password = "";

			// check if user wants to set a password
			if (line.hasOption('p')) {
				password = line.getOptionValue('p');
				
				password = trySHA(password);
				
				// check if the user wants to create a user
				if (line.hasOption("c")) {
					String[] values = line.getOptionValues("c");

					if (values[0].isBlank()) {
						System.out.println("Did not specify a login name");
						System.exit(1);
					}

					UUID response;
					if (values.length < 2) {
						response = stub.create(line.getOptionValue('c'), System.getProperty("user.name"),
								password);
					} else {
						response = stub.create(line.getOptionValue('c'), values[1], password);
					}

					if (response != null) {
						System.out.println("Create user who's uuid is: " + response);
					} else {
						System.out.println("User already exists!");
					}
					System.exit(0);
				}

				if (line.hasOption('d')) {
					String[] op = line.getOptionValues('d');
					if (stub.delete(op[0], password)) {
						System.out.println("Deleted user: " + op[0]);
					} else {
						System.out.println("Wrong username or password to delete user");
					}
					System.exit(0);
				}

				// user wants to get lists of information
				if (line.hasOption('m')) {
					String[] op = line.getOptionValues('m');

					if (op[0].isBlank()) {
						System.out.println("Did not specify a proper old login name");
						System.exit(1);
					}

					if (op[1].isBlank()) {
						System.out.println("Did not specify a proper new login name");
						System.exit(1);
					}

					if (stub.modify(op[0], op[1], password)) {
						System.out.println("Modified user: " + op[0]);
					} else {
						System.out.println("Incorrect credentials");
					}
					System.exit(0);
				}
			} else {
				System.out.println("Password required to do privledged operations!");
				System.exit(1);
			}

		} catch (Exception e) {
			System.err.println("Client exception: " + e.toString());
			e.printStackTrace();
		}
	}
}
