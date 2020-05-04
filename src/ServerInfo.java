import java.io.Serializable;
public class ServerInfo implements Comparable<ServerInfo>,Serializable{
    static public final long serialVersionUID = 239423409294L;

    public ServerInfo(int pid, String address){
        this.pid = pid;
        this.address = address;
    }

    public int getPID(){
        return pid;
    }

    public String getAddress(){
        return address;
    }

    public void setPID(int pid){
        this.pid = pid;
    }

    public void setAddress(String newAddress){
        this.address = newAddress;
    }

    private int pid = -1;
    private String address = null;

    @Override
    public int compareTo(ServerInfo other){
        if(other.pid > pid) return -1;
        if(other.pid < pid) return  1;
        if(other.address == address){
            return 0;
        }

        //State inconsistent, cannot continue
        throw new RuntimeException("PID allocation failed: Two distinct servers have the same PID, cannot run election");
    }

    //trash langauge requires us to do this
    //private void writeObject(java.io.ObjectOutputStream out) throws IOException{
    //}

    //private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException{

    //}

    //private void readObjectNoData() throws ObjectStreamException{
    //    this.pid = -1;
    //    this.address = null;
    //}

}
