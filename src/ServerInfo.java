public class ServerInfo implements Comparable<ServerInfo>{
    public int pid = 0;
    public String address = null;

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
}
