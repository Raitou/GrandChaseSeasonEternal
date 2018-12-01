package client;

import java.io.OutputStream;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.Vector;

import msg.KFriendInfo;
import msg.MsgFunction;
import packet.Crypto;
import packet.OpcodeMsg;
import packet.Packet;
import server.Main;
import server.Server;
import server.SocketRecvHandler;
import util.Convert;

public class MsgClient extends Client {
	public Server server = null;
	
	// **********
	public int UserUID;
	public String UserID;
	public String UserNick;
	public String UserServerName;
	public String UserLocation;
	public int UserLocationNum;
	public boolean UserIsGamming;
	public int UserGuildID;
	
	public Vector<KFriendInfo> vecFriend;
	// **********
	
	public MsgClient(Socket s, Server srv)  {
		isClosed = false;
		this.s = s;
		this.server = srv;
		client_type = Client.MSG_CLIENT;
		
		sh = new SocketRecvHandler(this);
		sh.start();
		
		Main.printmsg("[" + server.serverName + "] 새로운 클라이언트 접속 (" + s.getInetAddress().getHostAddress() + ":" + s.getPort() + ")");
		
		// 패킷 암호화 키 초기화
		SecureRandom sr = new SecureRandom();
		sr.nextBytes(packetPrefix);
		sr.nextBytes(packetKey);
		sr.nextBytes(packetHmac);
		
		// 처음 보내는 패킷
		isFirstPacket = true;
		Packet p = new Packet(OpcodeMsg.EVENT_ACCEPT_CONNECTION_NOT);
		p.write(packetPrefix);
		p.writeInt(8);
		p.write(packetHmac);
		p.writeInt(8);
		p.write(packetKey);
		p.writeInt(1);
		p.skip(8);
		sendPacket(p, false);
		p = null;
		isFirstPacket = false;
	}
	
	// 패킷 보내기
	public void sendPacket(Packet p, boolean compress) {
		p.applyWrite();
		
		byte[] sendbuffer = null;
		if( isFirstPacket )
			sendbuffer = Crypto.AssemblePacket(p, Crypto.GC_DES_KEY, Crypto.GC_HMAC_KEY, new byte[] {0, 0}, packetNum++, compress);
		else
			sendbuffer = Crypto.AssemblePacket(p, packetKey, packetHmac, packetPrefix, packetNum++, compress);
		
		//System.out.println( Convert.byteArrayToHexString(p.buffer) );
		
		try {
			OutputStream os = s.getOutputStream();
			os.write(sendbuffer);
			os.flush();
		}catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	// 패킷 수신
	public void onPacket(Packet p) {
		Crypto.DecryptPacket(p, packetKey);
		
		int Opcode = p.readShort();
		int dataSize = p.readInt();
		int isCompressed = p.readByte();
		if( isCompressed == 1 ) p.readInt();
		
		//Main.printmsg(Opcode + "패킷 수신 (" + dataSize + "바이트)\n" + Convert.byteArrayToHexString(p.buffer));
		
		switch( Opcode ) {
		case OpcodeMsg.EVENT_HEART_BIT:
			heartbit = System.currentTimeMillis();
			break;
		case OpcodeMsg.EMS_S2_VERIFY_ACCOUNT_REQ:
			MsgFunction.EMS_S2_VERIFY_ACCOUNT_REQ(this, p);
			break;
		default:
			Main.printmsg("[" + server.serverName + "] 정의되지 않은 패킷 수신 (" + dataSize + "바이트)\n" + Convert.byteArrayToHexString(p.buffer));
			break;
		}
	}
	
	@Override
	public  void close() {
		try {
			Main.printmsg("[" + server.serverName + "] 클라이언트 접속 해제 (" + s.getInetAddress().getHostAddress() + ":" + s.getPort() + ")");
		
			sh.interrupt();
			s.close();
		
			isClosed = true; // 끊어졌다고 표시한다. PingPong이 객체 삭제해야함.
			s = null;
			sh = null;
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
