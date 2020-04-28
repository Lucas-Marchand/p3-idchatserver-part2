import java.time.Instant;
import java.util.UUID;
public class User {

	private UUID uuid;
    private String loginName;
    private String realName;
    public byte[] password;
    private Instant timeCreated = Instant.now();
    private Instant timeLastModified = Instant.now();

    public User (String login, UUID userUUID, String real, byte[] password){
        this.loginName = login;
        this.uuid = userUUID;
        this.realName = real;
        this.password = password;
    }

    @Override
    public String toString() {
        return "Login Name: " + loginName + " Real Name: " + realName + " UUID: " + uuid;
    }

	public Instant getTimeLastModified() {
		return timeLastModified;
	}

	public void setTimeLastModified(Instant timeLastModified) {
		this.timeLastModified = timeLastModified;
	}
}