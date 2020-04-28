import java.util.UUID;
import java.rmi.RemoteException;

public interface Id extends java.rmi.Remote
{
	/**
	 * Changes the old login name to have the new login name
	 * @param oldLoginName
	 * @param newLoginName
	 * @param password
	 * @return
	 * @throws RemoteException
	 */
    public boolean modify(String oldLoginName, String newLoginName, String password) throws RemoteException;

    /**
     * Creates a new user with login name, real name, and password
     * @param loginName
     * @param realName
     * @param password
     * @return
     * @throws RemoteException
     */
    public UUID create(String loginName, String realName, String password) throws RemoteException;

    /**
     * Queries the database for a particular user with loginName
     * @param loginName
     * @return
     * @throws RemoteException
     */
    public String lookup(String loginName) throws RemoteException;

    /**
     * Queries the database for a particular user with uuid
     * @param uuid
     * @return
     * @throws RemoteException
     */
    public String reverseLookup(UUID uuid) throws RemoteException;

    /**
     * Returns all of the users and uuid's corresponding to those users
     * @param listToGet
     * @return
     * @throws RemoteException
     */
    public String get(String listToGet) throws RemoteException;

    /**
     * Removes a user form the IdServer with loginName
     * @param loginName
     * @param password
     * @return
     * @throws RemoteException
     */
    public boolean delete(String loginName, String password) throws RemoteException;
    
    /**
     * Forces checkpoint in database to persist data to file.
     * @throws RemoteException
     */
    public void persistData() throws RemoteException;
}
