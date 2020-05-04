import java.time.Instant;
import java.util.UUID;
import java.io.Serializable;
/**
 * Represents a User in the IdServer Database
 */
public class User implements Serializable{
    static public final long serialVersionUID = 236423409294L;

	private UUID uuid;
    private String loginName;
    private String realName;
    private String password;
    private Instant timeCreated = Instant.now();
    private Instant timeLastModified = Instant.now();

    /**
     * Constructs a new User
     * @param login the name of the new user
     * @param userUUID the uuid of the new user in the IdServer database
     * @param real real name of the user
     * @param password the password of the new user
     */
    public User (String login, UUID userUUID, String real, String password){
        this.loginName = login;
        this.uuid = userUUID;
        this.realName = real;
        this.password = password;
    }

    /**
     * gets the password of the user
     * @return the password of the user
     */
    public String getPassword(){
        return password;
    }

    /**
     * Gets the login name of the user
     * @return login name of user
     */
    public String getLoginName(){
        return loginName;
    }

    /**
     * Getter for the users UUID
     * @return UUID of the user
     */
    public UUID getUUID(){
        return uuid;
    }


    @Override
    public String toString() {
        return "Login Name: " + loginName + " Real Name: " + realName + " UUID: " + uuid;
    }

    /**
     * Getter for the last time the user was modified
     * @return the Instant that the user was modified
     */
	public Instant getTimeLastModified() {
		return timeLastModified;
	}

    /**
     * Setter for the time the user was modified
     * @param timeLastModified Instant the user was last modified
     */
	public void setTimeLastModified(Instant timeLastModified) {
		this.timeLastModified = timeLastModified;
	}
}
