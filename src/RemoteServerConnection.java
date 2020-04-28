import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class RemoteServerConnection implements Runnable{

	private int port;
	public RemoteServerConnection (int port) {
		this.port = port;
	}
	
	@Override
	public void run() {

		/*
		 * When one of the 
		 */
		DatagramSocket socket = null;
		try {
			socket = new DatagramSocket(port);
			
			byte[] receiveBuffer = new byte[1024];
			byte[] sendBuffer = socket.getInetAddress().getAddress();
			
			DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length, port);
			
			while (true) {
				socket.receive(receivePacket);
				DatagramPacket sendPacket = new DatagramPacket(sendBuffer, sendBuffer.length, receivePacket.getAddress(), receivePacket.getPort());
				socket.send(sendPacket);
			}
		} catch (IOException e) {
			if(socket != null) {
				socket.close();				
			}
			System.exit(1);
		}
	}
}
