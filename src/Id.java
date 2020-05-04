import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.rmi.RemoteException;

public interface Id extends java.rmi.Remote
{
	/**
	 * Changes the old login name to have the new login name
	 * @param oldLoginName the old login name of the user
	 * @param newLoginName the new login name of the user
	 * @param password the password of the user
	 * @return true if the user was successfully modified else false
	 * @throws RemoteException
	 */
    public boolean modify(String oldLoginName, String newLoginName, String password) throws RemoteException;

    /**
     * Creates a new user with login name, real name, and password
     * @param loginName the login name of the newly created user
     * @param realName the real name of the newly created user
     * @param password the password of the new user
     * @return the UUID assigned to the newly created user
     * @throws RemoteException
     */
    public UUID create(String loginName, String realName, String password) throws RemoteException;

    /**
     * Queries the database for a particular user with loginName
     * @param loginName the login name of the user to lookup
     * @return the user with info 
     * @throws RemoteException
     */
    public String lookup(String loginName) throws RemoteException;

    /**
     * Queries the database for a particular user with uuid
     * @param uuid the uuid of the user to look for
     * @return the name of the user
     * @throws RemoteException
     */
    public String reverseLookup(UUID uuid) throws RemoteException;

    /**
     * Returns all of the users and uuid's corresponding to those users
     * @param listToGet the command of what kind of information to get from server
     * @return the query of the users of the databse from the given listToGet
     * @throws RemoteException
     */
    public String get(String listToGet) throws RemoteException;

    /**
     * Removes a user form the IdServer with loginName
     * @param loginName username of user to remove
     * @param password the password of the user to remove
     * @return true if the user could be removed else false
     * @throws RemoteException
     */
    public boolean delete(String loginName, String password) throws RemoteException;
    
    /**
     * Forces checkpoint in database to persist data to file.
     * @throws RemoteException
     */
    public void persistData() throws RemoteException;

    /**
     * Gets lookup user data from the lead server
     * @return the map of login names to users
     * @throws RemoteException
     */
	public Map<String, User> getLookupUsersDatabase() throws RemoteException;

    /**
     * gets reverse lookup user data from the lead server
     * @return the map of UUID's to users
     * @throws RemoteException
     */
	public Map<UUID, User> getReverseLookupUsersDatabase() throws RemoteException;

    /**
     * Used as part of the election. Servers with a lower pid should send this message to servers with a higher pid.
     * @param sender    Server sending this message. Used for logging and validation.
     * @return          True as long as the sender has a lower pid.
     */
    public boolean electionRequest(ServerInfo sender) throws RemoteException;

    /**
     * Used as part of the election. Sent by the new leader to all other servers.
     * @param newLeader The new leader server.
     */
    public void electionWon(ServerInfo newLeader) throws RemoteException;

    /** 
     * Returns the current leader, and checks that it is alive. If the leader is not alive, an election is held in the background, and the new leader is returned.
     * @return the current leader.
     */
    public ServerInfo currentLeader() throws RemoteException;

    /** Dummy method used to check if server is alive.
     * 
     */
    public boolean isAlive() throws RemoteException;

    /** Adds a new backup server. Returns a list of all servers, including the callee with a pid assigned.
     * @param newServerAddress IP address of the sender.
     * @return a list of all servers.
     */
    public ArrayList<ServerInfo> registerServer(String newServerAddress) throws RemoteException;

    /** Adds a new server to the list of standby servers.
     * @param newServer the new server to be added to standby servers
     */
    public void addNewServer(ServerInfo newServer) throws RemoteException;

}
